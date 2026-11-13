package com.br.remote_agent.security;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable credentials container holding the computerId and agentToken.
 */
public record AgentCredentials(UUID computerId, String agentToken) implements Serializable {

    public AgentCredentials {
        Objects.requireNonNull(computerId, "computerId must not be null");
        Objects.requireNonNull(agentToken, "agentToken must not be null");
        String trimmedToken = agentToken.trim();
        if (trimmedToken.isEmpty()) {
            throw new IllegalArgumentException("agentToken must not be blank");
        }
        agentToken = trimmedToken;
    }

    /**
     * Returns a masked representation of the token for safe logging/display.
     */
    public String maskedToken() {
        if (agentToken.length() <= 8) {
            return "********";
        }
        return agentToken.substring(0, 4) + "..." + agentToken.substring(agentToken.length() - 4);
    }

    @Override
    public String toString() {
        return "AgentCredentials[computerId=" + computerId + ", agentToken=" + maskedToken() + "]";
    }
}
