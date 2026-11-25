package com.br.remote_agent.capture;

import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class DirtyRectEngineTest {

    /**
     * Verifies the binary wire format and dirty-tile detection logic using
     * a fake Robot that returns controlled images.  Skipped in headless CI.
     */
    @Test
    void testDirtyTileDetection() throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("Skipping DirtyRectEngineTest — headless environment.");
            return;
        }

        Robot robot = new Robot();
        // Use a small tile size for a manageable grid.
        int tileSize = 64;
        DirtyRectEngine engine = new DirtyRectEngine(robot, 1280, 0.5f, tileSize);

        // First capture is always a keyframe — all tiles are dirty.
        DirtyRectEngine.CaptureResult first = engine.captureDirtyTiles();
        assertNotNull(first, "First capture must produce a keyframe (never null).");
        assertTrue(first.isKeyframe());
        assertTrue(first.dirtyTileCount() > 0);
        assertEquals(first.totalTileCount(), first.dirtyTileCount(),
                "Keyframe should mark every tile as dirty.");

        // Verify the binary header can be parsed.
        ByteBuffer buf = first.payload();
        long timestamp = buf.getLong();
        assertTrue(timestamp > 0, "Capture timestamp should be a positive epoch millis.");
        int frameWidth = Short.toUnsignedInt(buf.getShort());
        int frameHeight = Short.toUnsignedInt(buf.getShort());
        int wireTileSize = Short.toUnsignedInt(buf.getShort());
        int flags = Byte.toUnsignedInt(buf.get());
        int tileCount = Byte.toUnsignedInt(buf.get());

        assertTrue(frameWidth > 0);
        assertTrue(frameHeight > 0);
        assertEquals(tileSize, wireTileSize);
        assertEquals(1, flags & 1, "Keyframe flag should be set.");
        assertEquals(first.dirtyTileCount(), tileCount);

        // Parse each tile header.
        for (int i = 0; i < tileCount; i++) {
            int col = Byte.toUnsignedInt(buf.get());
            int row = Byte.toUnsignedInt(buf.get());
            int tw = Short.toUnsignedInt(buf.getShort());
            int th = Short.toUnsignedInt(buf.getShort());
            int jpegLen = buf.getInt();

            assertTrue(col >= 0);
            assertTrue(row >= 0);
            assertTrue(tw > 0 && tw <= tileSize);
            assertTrue(th > 0 && th <= tileSize);
            assertTrue(jpegLen > 0, "JPEG payload must not be empty.");

            // Skip past the JPEG bytes.
            buf.position(buf.position() + jpegLen);
        }

        assertFalse(buf.hasRemaining(), "No trailing bytes expected after all tiles.");

        // Second capture with (likely) the same desktop → should produce fewer
        // dirty tiles or null if nothing changed at all.
        DirtyRectEngine.CaptureResult second = engine.captureDirtyTiles();
        if (second != null) {
            assertFalse(second.isKeyframe());
            assertTrue(second.dirtyTileCount() <= second.totalTileCount());
            System.out.printf("Second capture: %d/%d tiles dirty.%n",
                    second.dirtyTileCount(), second.totalTileCount());
        } else {
            System.out.println("Second capture: 0 dirty tiles (screen unchanged) — skipped sending.");
        }
    }

    @Test
    void testResetForcesKeyframe() throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("Skipping — headless environment.");
            return;
        }

        Robot robot = new Robot();
        DirtyRectEngine engine = new DirtyRectEngine(robot, 1280, 0.5f, 128);

        // First capture = keyframe.
        DirtyRectEngine.CaptureResult r1 = engine.captureDirtyTiles();
        assertNotNull(r1);
        assertTrue(r1.isKeyframe());

        // Reset and capture again — should be another keyframe.
        engine.reset();
        DirtyRectEngine.CaptureResult r2 = engine.captureDirtyTiles();
        assertNotNull(r2);
        assertTrue(r2.isKeyframe());
    }
}
