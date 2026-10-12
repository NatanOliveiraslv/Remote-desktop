package com.br.remote_server.dtos.websocket;

import jakarta.validation.constraints.NotBlank;

public record AgentConnectionAccepted(
    @NotBlank
    String type,
    @NotBlank
    String remoteSessionId
) {
    
}
