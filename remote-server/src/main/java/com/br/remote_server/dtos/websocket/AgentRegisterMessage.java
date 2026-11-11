package com.br.remote_server.dtos.websocket;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AgentRegisterMessage (
    @NotBlank
    String type,
    @NotNull
    UUID computerId
) {
    public AgentRegisterMessage(String type, UUID computerId) {
        this.type = type;
        this.computerId = computerId;
    }
}
