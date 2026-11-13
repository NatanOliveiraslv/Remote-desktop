package com.br.remote_server.dtos.computers;

import java.util.UUID;

import com.br.remote_server.models.Computer;

public record ComputerCreatedResponseDTO (
    UUID id,
    String nameComputer,
    String operatingSystem, 
    String agentVersion,
    String agentToken
){
    public ComputerCreatedResponseDTO(Computer computer, String agentToken) {
        this(
            computer.getId(),
            computer.getNameComputer(),
            computer.getOperatingSystem(),
            computer.getAgentVersion(),
            agentToken
        );
    }
}
