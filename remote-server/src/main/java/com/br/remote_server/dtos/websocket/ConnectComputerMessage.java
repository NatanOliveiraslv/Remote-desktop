package com.br.remote_server.dtos.websocket;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ConnectComputerMessage (
    @NotBlank
    String type,
    @NotNull 
    UUID computerId
) {
    public ConnectComputerMessage(String type, UUID computerId) {
        this.type = type;
        this.computerId = computerId;
    }
}

