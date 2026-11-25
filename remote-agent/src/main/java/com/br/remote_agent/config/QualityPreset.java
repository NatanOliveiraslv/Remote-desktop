package com.br.remote_agent.config;

/**
 * Named quality presets selectable from the remote client or agent config.
 */
public enum QualityPreset {
    LOW(960, 0.35f, 0.25f, 0.50f),
    MEDIUM(1280, 0.55f, 0.35f, 0.78f),
    HIGH(1920, 0.80f, 0.55f, 0.92f);

    private final int maxWidth;
    private final float baseQuality;
    private final float minQuality;
    private final float maxQuality;

    QualityPreset(int maxWidth, float baseQuality, float minQuality, float maxQuality) {
        this.maxWidth = maxWidth;
        this.baseQuality = baseQuality;
        this.minQuality = minQuality;
        this.maxQuality = maxQuality;
    }

    public int maxWidth() {
        return maxWidth;
    }

    public float baseQuality() {
        return baseQuality;
    }

    public float minQuality() {
        return minQuality;
    }

    public float maxQuality() {
        return maxQuality;
    }

    public static QualityPreset fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return MEDIUM;
        }
        return switch (raw.trim().toUpperCase()) {
            case "LOW", "BAIXA" -> LOW;
            case "HIGH", "ALTA" -> HIGH;
            default -> MEDIUM;
        };
    }
}
