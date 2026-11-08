package com.br.remote_server.dtos.websocket;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

public record AgentConnectionAccepted(
    @NotBlank
    String type,
    @NotBlank
    UUID remoteSessionId
) {
    
}
