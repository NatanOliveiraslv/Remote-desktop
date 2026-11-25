package com.br.remote_agent.control;

import java.awt.AWTException;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.event.InputEvent;

/** Executes local desktop actions and captures the primary screen. */
public final class RemoteDesktopController {

    private final Robot robot;

    public RemoteDesktopController() throws AWTException {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException(
                    "The Agent must run in a desktop session, not a headless environment.");
        }
        this.robot = new Robot();
        this.robot.setAutoDelay(5);
    }

    /** Returns the underlying Robot for use by the capture engine. */
    public Robot getRobot() {
        return robot;
    }

    /**
     * Returns the primary screen bounds in logical coordinates (DIPs),
     * matching the coordinate space expected by {@link Robot#mouseMove(int, int)}
     * even when Windows HiDPI display scaling (e.g. 125%, 150%) is active.
     */
    public Rectangle getPrimaryScreenBounds() {
        GraphicsConfiguration gc = GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getDefaultScreenDevice()
                .getDefaultConfiguration();
        return gc.getBounds();
    }

    /**
     * Returns the HiDPI scale factor of the primary monitor (e.g. 1.25 for 125%).
     */
    public double getPrimaryScreenScale() {
        GraphicsConfiguration gc = GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getDefaultScreenDevice()
                .getDefaultConfiguration();
        AffineTransform tx = gc.getDefaultTransform();
        return tx != null ? tx.getScaleX() : 1.0;
    }

    /**
     * Moves the local mouse when coordinates are within the primary screen logical bounds.
     */
    public boolean moveMouse(int x, int y) {
        Rectangle bounds = getPrimaryScreenBounds();

        if (x < 0 || y < 0 || x >= bounds.width || y >= bounds.height) {
            System.out.printf(
                    "Ignoring mouse coordinates outside primary screen: x=%d, y=%d (logical %dx%d)%n",
                    x, y, bounds.width, bounds.height);
            return false;
        }

        robot.mouseMove(bounds.x + x, bounds.y + y);
        return true;
    }

    public void pressMouse(String button) {
        robot.mousePress(getButtonMask(button));
    }

    public void releaseMouse(String button) {
        robot.mouseRelease(getButtonMask(button));
    }

    private int getButtonMask(String button) {
        return switch (button) {
            case "LEFT" -> InputEvent.BUTTON1_DOWN_MASK;
            case "MIDDLE" -> InputEvent.BUTTON2_DOWN_MASK;
            case "RIGHT" -> InputEvent.BUTTON3_DOWN_MASK;
            default -> throw new IllegalArgumentException(
                    "Invalid mouse button: " + button);
        };
    }

    /**
     * Captures the primary screen and returns it as a compressed JPEG byte array.
     */
    public byte[] captureScreenJpeg(int maxWidth, float quality) throws IOException {
        if (maxWidth < 1) {
            throw new IllegalArgumentException("maxWidth must be positive");
        }
        if (quality < 0.1f || quality > 1.0f) {
            throw new IllegalArgumentException("quality must be between 0.1 and 1.0");
        }

        // DIAGNÓSTICO TEMPORÁRIO (issue #3): descobrir qual etapa está lenta.
        // Remover depois de identificar o gargalo.
        long diagT0 = System.currentTimeMillis();

        Rectangle bounds = getPrimaryScreenBounds();
        BufferedImage screenshot = robot.createScreenCapture(bounds);

        long diagT1 = System.currentTimeMillis();

        BufferedImage imageToEncode = screenshot;
        if (screenshot.getWidth() > maxWidth) {
            int targetHeight = Math.max(
                    1,
                    (int) Math.round(
                            screenshot.getHeight()
                                    * (maxWidth / (double) screenshot.getWidth())));

            BufferedImage resized = new BufferedImage(
                    maxWidth,
                    targetHeight,
                    BufferedImage.TYPE_INT_RGB);
            var graphics = resized.createGraphics();
            try {
                graphics.setRenderingHint(
                        java.awt.RenderingHints.KEY_INTERPOLATION,
                        java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.drawImage(screenshot, 0, 0, maxWidth, targetHeight, null);
            } finally {
                graphics.dispose();
            }
            imageToEncode = resized;
        }

        long diagT2 = System.currentTimeMillis();

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("No JPEG ImageIO writer is available");
        }

        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {

            writer.setOutput(imageOutput);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            if (parameters.canWriteCompressed()) {
                parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                parameters.setCompressionQuality(quality);
            }

            writer.write(null, new IIOImage(imageToEncode, null, null), parameters);
            imageOutput.flush();
            byte[] jpeg = output.toByteArray();

            long diagT3 = System.currentTimeMillis();
            System.out.printf(
                    "Screen capture stages: screen=%dx%d -> %dx%d, capture=%dms, resize=%dms, encode=%dms, total=%dms%n",
                    screenshot.getWidth(), screenshot.getHeight(),
                    imageToEncode.getWidth(), imageToEncode.getHeight(),
                    diagT1 - diagT0, diagT2 - diagT1, diagT3 - diagT2, diagT3 - diagT0);

            return jpeg;
        } finally {
            writer.dispose();
        }
    }
}
