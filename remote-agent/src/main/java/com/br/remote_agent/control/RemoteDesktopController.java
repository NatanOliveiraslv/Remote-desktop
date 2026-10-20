package com.br.remote_agent.control;

import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
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

    /**
     * Moves the local mouse when coordinates are within the primary screen bounds.
     */
    public boolean moveMouse(int x, int y) {
        Dimension size = Toolkit.getDefaultToolkit().getScreenSize();

        if (x < 0 || y < 0 || x >= size.width || y >= size.height) {
            System.out.printf(
                    "Ignoring mouse coordinates outside primary screen: x=%d, y=%d (screen %dx%d)%n",
                    x, y, size.width, size.height);
            return false;
        }

        robot.mouseMove(x, y);
        System.out.printf("Mouse moved to x=%d, y=%d%n", x, y);
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

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        BufferedImage screenshot = robot.createScreenCapture(
                new Rectangle(0, 0, screenSize.width, screenSize.height));

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
            return output.toByteArray();
        } finally {
            writer.dispose();
        }
    }
}
