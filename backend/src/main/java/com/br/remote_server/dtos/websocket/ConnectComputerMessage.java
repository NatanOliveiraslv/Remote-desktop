package com.br.remote_server.dtos.websocket;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

public record ConnectComputerMessage (
    @NotBlank
    String type,
    @NotBlank
    UUID computerId
) {
    public ConnectComputerMessage(String type, UUID computerId) {
        this.type = type;
        this.computerId = computerId;
    }
}

