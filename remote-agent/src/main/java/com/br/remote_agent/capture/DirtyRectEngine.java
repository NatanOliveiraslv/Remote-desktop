package com.br.remote_agent.capture;

import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.MultiResolutionImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.CRC32;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStreamImpl;

/**
 * Dirty-rectangle screen capture engine.
 * <p>
 * Divides the (possibly down-scaled) screen into a grid of fixed-size tiles.
 * On each capture cycle it compares a fast checksum of every tile's raw pixel
 * data with the previous frame and JPEG-encodes only the tiles that changed,
 * using parallel per-thread encoders and zero-allocation output streams.
 */
public final class DirtyRectEngine {

    /** Default tile side length in pixels. */
    public static final int DEFAULT_TILE_SIZE = 128;

    /** Force a full keyframe every N captures as a periodic consistency safety net. */
    private static final int KEYFRAME_INTERVAL = 300;

    /** Minimum dirty tiles to dispatch to the parallel encoder pool. */
    private static final int PARALLEL_ENCODE_THRESHOLD = 3;

    private static final int ENCODE_THREADS = Math.max(2,
            Math.min(8, Runtime.getRuntime().availableProcessors()));

    private final Robot robot;
    private final int tileSize;
    private volatile int maxWidth;
    private volatile float jpegQuality;
    private volatile boolean forceNextKeyframe = false;

    // Grid dimensions (recomputed if screen resolution changes).
    private int frameWidth;
    private int frameHeight;
    private int cols;
    private int rows;

    // Reusable scaled image & graphics to avoid allocating 1280x720 RGB every frame.
    private BufferedImage reusableScaled;
    private Graphics2D reusableGraphics;

    // Reusable row buffer for CRC32.
    private final byte[] rowBuf;
    private final CRC32 crc32 = new CRC32();

    // Previous-frame checksums per tile. null means "first frame / resolution changed → keyframe".
    private long[] previousChecksums;
    private int frameCounter;

    // Dedicated parallel pool for JPEG encoding of dirty tiles.
    private final ExecutorService encodePool = Executors.newFixedThreadPool(ENCODE_THREADS, r -> {
        Thread t = new Thread(r, "dirty-tile-encoder");
        t.setDaemon(true);
        return t;
    });

    // Per-thread reusable JPEG encoder (avoids ImageIO SPI lookup & cache allocs per tile).
    private final ThreadLocal<TileEncoder> threadEncoder;

    public DirtyRectEngine(Robot robot, int maxWidth, float jpegQuality) {
        this(robot, maxWidth, jpegQuality, DEFAULT_TILE_SIZE);
    }

    public DirtyRectEngine(Robot robot, int maxWidth, float jpegQuality, int tileSize) {
        if (maxWidth < 1) throw new IllegalArgumentException("maxWidth must be positive");
        if (jpegQuality < 0.1f || jpegQuality > 1.0f) throw new IllegalArgumentException("jpegQuality must be in [0.1, 1.0]");
        if (tileSize < 16 || tileSize > 512) throw new IllegalArgumentException("tileSize must be in [16, 512]");
        this.robot = robot;
        this.maxWidth = maxWidth;
        this.jpegQuality = jpegQuality;
        this.tileSize = tileSize;
        this.rowBuf = new byte[tileSize * 4];
        this.threadEncoder = ThreadLocal.withInitial(() -> new TileEncoder(tileSize));
    }

    public int getMaxWidth() {
        return maxWidth;
    }

    public void setMaxWidth(int maxWidth) {
        if (maxWidth < 320) throw new IllegalArgumentException("maxWidth must be >= 320");
        this.maxWidth = maxWidth;
    }

    public float getJpegQuality() {
        return jpegQuality;
    }

    public void setJpegQuality(float jpegQuality) {
        this.jpegQuality = Math.max(0.1f, Math.min(1.0f, jpegQuality));
    }

    public void forceKeyframe() {
        this.forceNextKeyframe = true;
    }

    // ─────────────────────── public API ───────────────────────

    public CaptureResult captureDirtyTiles() throws IOException {
        long t0 = System.currentTimeMillis();

        // 1. Capture primary screen using HiDPI-aware physical resolution when available.
        BufferedImage screenshot = captureHiDpiScreen();

        long t1 = System.currentTimeMillis();

        // 2. Down-scale into reusable buffer if needed.
        int currentMaxWidth = this.maxWidth;
        float currentQuality = this.jpegQuality;
        BufferedImage scaled = scaleIfNeeded(screenshot, currentMaxWidth);
        int sw = scaled.getWidth();
        int sh = scaled.getHeight();

        long t2 = System.currentTimeMillis();

        // 3. Detect resolution change → force keyframe reset.
        if (sw != frameWidth || sh != frameHeight) {
            frameWidth = sw;
            frameHeight = sh;
            cols = ceilDiv(sw, tileSize);
            rows = ceilDiv(sh, tileSize);
            previousChecksums = null;
            frameCounter = 0;
        }

        boolean isKeyframe = forceNextKeyframe
                || (previousChecksums == null)
                || (frameCounter % KEYFRAME_INTERVAL == 0);
        forceNextKeyframe = false;
        frameCounter++;

        // 4. Fast checksum comparison directly on the backing int[] array.
        int[] pixels = getPixelArray(scaled);
        long[] currentChecksums = new long[cols * rows];
        List<DirtyTile> dirtyTiles = new ArrayList<>();

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int tx = col * tileSize;
                int ty = row * tileSize;
                int tw = Math.min(tileSize, sw - tx);
                int th = Math.min(tileSize, sh - ty);

                long crc = checksumTile(pixels, sw, tx, ty, tw, th);
                int idx = row * cols + col;
                currentChecksums[idx] = crc;

                boolean changed = isKeyframe
                        || previousChecksums == null
                        || previousChecksums[idx] != crc;

                if (changed) {
                    dirtyTiles.add(new DirtyTile(col, row, tw, th, null));
                }
            }
        }

        long t3 = System.currentTimeMillis();
        previousChecksums = currentChecksums;

        if (dirtyTiles.isEmpty()) {
            return null;
        }

        // 5. JPEG-encode only the dirty tiles (in parallel when >= threshold).
        encodeDirtyTiles(scaled, dirtyTiles, currentQuality);

        long t4 = System.currentTimeMillis();

        // 6. Serialise binary payload.
        ByteBuffer payload = serialise(t0, isKeyframe, dirtyTiles);
        int totalJpegBytes = 0;
        for (DirtyTile t : dirtyTiles) {
            totalJpegBytes += t.jpeg.length;
        }

        return new CaptureResult(
                payload,
                dirtyTiles.size(),
                cols * rows,
                totalJpegBytes,
                isKeyframe,
                t4 - t0,
                t1 - t0,
                t2 - t1,
                t3 - t2,
                t4 - t3
        );
    }

    public void reset() {
        previousChecksums = null;
        frameCounter = 0;
    }

    public void close() {
        encodePool.shutdownNow();
        if (reusableGraphics != null) {
            reusableGraphics.dispose();
            reusableGraphics = null;
        }
    }

    // ─────────────────── result record ────────────────────

    public record CaptureResult(
            ByteBuffer payload,
            int dirtyTileCount,
            int totalTileCount,
            int totalJpegBytes,
            boolean isKeyframe,
            long durationMs,
            long captureMs,
            long scaleMs,
            long diffMs,
            long encodeMs
    ) {}

    // ─────────────── internals ─────────────────

    private record DirtyTile(int col, int row, int pixelWidth, int pixelHeight, byte[] jpeg) {}

    private BufferedImage captureHiDpiScreen() {
        GraphicsConfiguration gc = GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getDefaultScreenDevice()
                .getDefaultConfiguration();
        Rectangle logicalBounds = gc.getBounds();

        MultiResolutionImage mrImage = robot.createMultiResolutionScreenCapture(logicalBounds);
        List<Image> variants = mrImage.getResolutionVariants();
        Image best = variants.get(variants.size() - 1);

        if (best instanceof BufferedImage bi) {
            return bi;
        }
        return robot.createScreenCapture(logicalBounds);
    }

    private void encodeDirtyTiles(BufferedImage scaled, List<DirtyTile> dirtyTiles, float quality) throws IOException {
        if (dirtyTiles.size() < PARALLEL_ENCODE_THRESHOLD) {
            TileEncoder encoder = threadEncoder.get();
            for (int i = 0; i < dirtyTiles.size(); i++) {
                DirtyTile dt = dirtyTiles.get(i);
                BufferedImage tileBuf = scaled.getSubimage(
                        dt.col * tileSize, dt.row * tileSize, dt.pixelWidth, dt.pixelHeight);
                byte[] jpeg = encoder.encode(tileBuf, quality);
                dirtyTiles.set(i, new DirtyTile(dt.col, dt.row, dt.pixelWidth, dt.pixelHeight, jpeg));
            }
            return;
        }

        List<Callable<byte[]>> tasks = new ArrayList<>(dirtyTiles.size());
        for (DirtyTile dt : dirtyTiles) {
            final int tx = dt.col * tileSize;
            final int ty = dt.row * tileSize;
            final int tw = dt.pixelWidth;
            final int th = dt.pixelHeight;
            tasks.add(() -> {
                // getSubimage shares the underlying DataBufferInt read-only across threads.
                BufferedImage tileBuf = scaled.getSubimage(tx, ty, tw, th);
                return threadEncoder.get().encode(tileBuf, quality);
            });
        }

        try {
            List<Future<byte[]>> futures = encodePool.invokeAll(tasks);
            for (int i = 0; i < dirtyTiles.size(); i++) {
                DirtyTile dt = dirtyTiles.get(i);
                byte[] jpeg = futures.get(i).get();
                dirtyTiles.set(i, new DirtyTile(dt.col, dt.row, dt.pixelWidth, dt.pixelHeight, jpeg));
            }
        } catch (Exception e) {
            throw new IOException("Parallel tile JPEG encoding failed", e);
        }
    }

    private BufferedImage scaleIfNeeded(BufferedImage src, int targetMaxWidth) {
        if (src.getWidth() <= targetMaxWidth) {
            return ensureIntRgb(src);
        }
        int targetHeight = Math.max(1,
                (int) Math.round(src.getHeight() * (targetMaxWidth / (double) src.getWidth())));

        if (reusableScaled == null
                || reusableScaled.getWidth() != targetMaxWidth
                || reusableScaled.getHeight() != targetHeight) {
            if (reusableGraphics != null) {
                reusableGraphics.dispose();
            }
            reusableScaled = new BufferedImage(targetMaxWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            reusableGraphics = reusableScaled.createGraphics();
            reusableGraphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            reusableGraphics.setRenderingHint(
                    RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_SPEED);
        }

        reusableGraphics.drawImage(src, 0, 0, targetMaxWidth, targetHeight, null);
        return reusableScaled;
    }

    private BufferedImage ensureIntRgb(BufferedImage img) {
        if (img.getType() == BufferedImage.TYPE_INT_RGB || img.getType() == BufferedImage.TYPE_INT_ARGB) {
            return img;
        }
        if (reusableScaled == null
                || reusableScaled.getWidth() != img.getWidth()
                || reusableScaled.getHeight() != img.getHeight()) {
            if (reusableGraphics != null) {
                reusableGraphics.dispose();
            }
            reusableScaled = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
            reusableGraphics = reusableScaled.createGraphics();
        }
        reusableGraphics.drawImage(img, 0, 0, null);
        return reusableScaled;
    }

    private static int[] getPixelArray(BufferedImage img) {
        return ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
    }

    private long checksumTile(int[] pixels, int scanlineWidth,
                              int tx, int ty, int tw, int th) {
        crc32.reset();
        int rowBytes = tw * 4;
        for (int y = ty; y < ty + th; y++) {
            int offset = y * scanlineWidth + tx;
            for (int i = 0; i < tw; i++) {
                int px = pixels[offset + i];
                int bi = i * 4;
                rowBuf[bi]     = (byte) (px >>> 24);
                rowBuf[bi + 1] = (byte) (px >>> 16);
                rowBuf[bi + 2] = (byte) (px >>> 8);
                rowBuf[bi + 3] = (byte) px;
            }
            crc32.update(rowBuf, 0, rowBytes);
        }
        return crc32.getValue();
    }

    private ByteBuffer serialise(long captureTimestamp, boolean isKeyframe, List<DirtyTile> tiles) {
        int headerSize = 16;
        int tilesPayload = 0;
        for (DirtyTile t : tiles) {
            tilesPayload += 10 + t.jpeg.length;
        }

        ByteBuffer buf = ByteBuffer.allocate(headerSize + tilesPayload);

        buf.putLong(captureTimestamp);
        buf.putShort((short) frameWidth);
        buf.putShort((short) frameHeight);
        buf.putShort((short) tileSize);
        buf.put((byte) (isKeyframe ? 1 : 0));
        buf.put((byte) tiles.size());

        for (DirtyTile t : tiles) {
            buf.put((byte) t.col);
            buf.put((byte) t.row);
            buf.putShort((short) t.pixelWidth);
            buf.putShort((short) t.pixelHeight);
            buf.putInt(t.jpeg.length);
            buf.put(t.jpeg);
        }

        buf.flip();
        return buf;
    }

    private static int ceilDiv(int a, int b) {
        return (a + b - 1) / b;
    }

    // ─────────── per-thread zero-alloc JPEG encoder ───────────

    private static final class TileEncoder {
        private final ImageWriter writer;
        private final ImageWriteParam params;
        private final ByteArrayOutputStream baos;
        private final DirectByteArrayOutputStream ios;

        TileEncoder(int tileSize) {
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (!writers.hasNext()) {
                throw new IllegalStateException("No JPEG ImageIO writer available");
            }
            this.writer = writers.next();
            this.params = writer.getDefaultWriteParam();
            if (params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            }
            this.baos = new ByteArrayOutputStream(tileSize * tileSize / 2);
            this.ios = new DirectByteArrayOutputStream(baos);
            this.writer.setOutput(ios);
        }

        byte[] encode(BufferedImage tile, float quality) throws IOException {
            if (params.canWriteCompressed()) {
                params.setCompressionQuality(quality);
            }
            baos.reset();
            ios.resetPosition();
            writer.write(null, new IIOImage(tile, null, null), params);
            return baos.toByteArray();
        }
    }

    /**
     * Minimal write-only ImageOutputStream backed directly by a reusable ByteArrayOutputStream,
     * bypassing ImageIO's SPI lookup and MemoryCacheImageOutputStream block allocations.
     */
    private static final class DirectByteArrayOutputStream extends ImageOutputStreamImpl {
        private final ByteArrayOutputStream out;

        DirectByteArrayOutputStream(ByteArrayOutputStream out) {
            this.out = out;
        }

        void resetPosition() {
            streamPos = 0;
            flushedPos = 0;
            bitOffset = 0;
        }

        @Override
        public void write(int b) {
            out.write(b);
            streamPos++;
        }

        @Override
        public void write(byte[] b, int off, int len) {
            out.write(b, off, len);
            streamPos += len;
        }

        @Override
        public int read() {
            throw new UnsupportedOperationException("Write-only stream");
        }

        @Override
        public int read(byte[] b, int off, int len) {
            throw new UnsupportedOperationException("Write-only stream");
        }
    }
}
