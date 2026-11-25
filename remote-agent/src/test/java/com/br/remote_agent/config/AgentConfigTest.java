package com.br.remote_agent.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AgentConfigTest {

    @Test
    void testLoadsFromPropertiesFile(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("agent.properties");
        Files.writeString(configFile, """
                agent.server-uri=ws://example.com:9090/ws/agent
                agent.screen.max-width=1920
                agent.screen.quality=0.82
                agent.screen.fps=15
                """);

        AgentConfig config = AgentConfig.load(new String[]{"--config", configFile.toString()});

        assertEquals("ws://example.com:9090/ws/agent", config.serverUri());
        assertEquals(1920, config.maxWidth());
        assertEquals(0.82f, config.jpegQuality(), 0.001f);
        assertEquals(15, config.targetFps());
    }

    @Test
    void testCliFlagsOverridePropertiesFile(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("agent.properties");
        Files.writeString(configFile, """
                agent.screen.max-width=1920
                agent.screen.quality=0.82
                """);

        AgentConfig config = AgentConfig.load(new String[]{
                "--config", configFile.toString(),
                "--max-width", "960",
                "--quality", "0.40",
                "--fps", "10"
        });

        assertEquals(960, config.maxWidth());
        assertEquals(0.40f, config.jpegQuality(), 0.001f);
        assertEquals(10, config.targetFps());
    }

    @Test
    void testQualityPresetMapping() {
        assertEquals(QualityPreset.LOW, QualityPreset.fromString("LOW"));
        assertEquals(QualityPreset.LOW, QualityPreset.fromString("baixa"));
        assertEquals(QualityPreset.HIGH, QualityPreset.fromString("high"));
        assertEquals(QualityPreset.HIGH, QualityPreset.fromString("ALTA"));
        assertEquals(QualityPreset.MEDIUM, QualityPreset.fromString("unknown"));
    }
}
