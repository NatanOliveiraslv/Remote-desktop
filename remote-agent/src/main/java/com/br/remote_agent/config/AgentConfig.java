package com.br.remote_agent.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Agent runtime configuration loaded from (highest priority first):
 * 1. CLI flags (--server-uri, --max-width, --quality, --fps, --credentials-file)
 * 2. JVM system properties (agent.*)
 * 3. Environment variables (AGENT_*)
 * 4. Optional properties file (agent.properties in working directory or --config)
 * 5. Sensible defaults
 */
public final class AgentConfig {

    public static final String DEFAULT_SERVER_URI = "ws://localhost:8080/api/v1/ws/agent";
    public static final String DEFAULT_CREDENTIALS_FILE = ".agent-credentials";
    public static final String DEFAULT_CONFIG_FILE = "agent.properties";

    public static final int DEFAULT_MAX_WIDTH = QualityPreset.MEDIUM.maxWidth();
    public static final float DEFAULT_QUALITY = QualityPreset.MEDIUM.baseQuality();
    public static final int DEFAULT_FPS = 12;

    private final String serverUri;
    private final Path credentialsFile;
    private final int maxWidth;
    private final float jpegQuality;
    private final int targetFps;

    public AgentConfig(
            String serverUri,
            Path credentialsFile,
            int maxWidth,
            float jpegQuality,
            int targetFps) {
        this.serverUri = serverUri;
        this.credentialsFile = credentialsFile;
        this.maxWidth = Math.max(320, Math.min(7680, maxWidth));
        this.jpegQuality = Math.max(0.1f, Math.min(1.0f, jpegQuality));
        this.targetFps = Math.max(1, Math.min(30, targetFps));
    }

    public String serverUri() {
        return serverUri;
    }

    public Path credentialsFile() {
        return credentialsFile;
    }

    public int maxWidth() {
        return maxWidth;
    }

    public float jpegQuality() {
        return jpegQuality;
    }

    public int targetFps() {
        return targetFps;
    }

    public static AgentConfig load(String[] args) {
        String configPathStr = resolveRaw(args, "--config", "agent.config", "AGENT_CONFIG_FILE", null, DEFAULT_CONFIG_FILE);
        Properties fileProps = loadPropertiesFile(Path.of(configPathStr));

        String serverUri = resolveRaw(
                args, "--server-uri", "agent.server-uri", "AGENT_SERVER_URI", fileProps, DEFAULT_SERVER_URI);
        String credentialsPath = resolveRaw(
                args, "--credentials-file", "agent.credentials.file", "AGENT_CREDENTIALS_FILE", fileProps, DEFAULT_CREDENTIALS_FILE);

        int maxWidth = parseInt(
                resolveRaw(args, "--max-width", "agent.screen.max-width", "AGENT_SCREEN_MAX_WIDTH", fileProps,
                        String.valueOf(DEFAULT_MAX_WIDTH)),
                DEFAULT_MAX_WIDTH);

        float quality = parseFloat(
                resolveRaw(args, "--quality", "agent.screen.quality", "AGENT_SCREEN_QUALITY", fileProps,
                        String.valueOf(DEFAULT_QUALITY)),
                DEFAULT_QUALITY);

        int fps = parseInt(
                resolveRaw(args, "--fps", "agent.screen.fps", "AGENT_SCREEN_FPS", fileProps,
                        String.valueOf(DEFAULT_FPS)),
                DEFAULT_FPS);

        return new AgentConfig(serverUri, Path.of(credentialsPath), maxWidth, quality, fps);
    }

    private static Properties loadPropertiesFile(Path path) {
        Properties props = new Properties();
        if (Files.isRegularFile(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                props.load(in);
            } catch (IOException e) {
                System.err.println("Warning: could not read config file " + path + ": " + e.getMessage());
            }
        }
        return props;
    }

    private static String resolveRaw(
            String[] args,
            String flagName,
            String sysProp,
            String envVar,
            Properties fileProps,
            String defaultValue) {
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                if (flagName.equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                    return args[i + 1].trim();
                }
            }
        }

        String propVal = System.getProperty(sysProp);
        if (propVal != null && !propVal.isBlank()) {
            return propVal.trim();
        }

        String envVal = System.getenv(envVar);
        if (envVal != null && !envVal.isBlank()) {
            return envVal.trim();
        }

        if (fileProps != null) {
            String fileVal = fileProps.getProperty(sysProp);
            if (fileVal != null && !fileVal.isBlank()) {
                return fileVal.trim();
            }
        }

        return defaultValue;
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static float parseFloat(String raw, float fallback) {
        try {
            return Float.parseFloat(raw.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
