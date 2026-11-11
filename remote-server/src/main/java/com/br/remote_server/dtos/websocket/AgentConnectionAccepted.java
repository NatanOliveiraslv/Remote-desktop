package com.br.remote_server.dtos.websocket;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AgentConnectionAccepted(
    @NotBlank
    String type,
    @NotNull
    UUID remoteSessionId
) {
    
}
