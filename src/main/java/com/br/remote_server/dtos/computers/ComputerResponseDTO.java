package com.br.remote_server.dtos.computers;

import java.util.UUID;

import com.br.remote_server.models.Computer;

public record ComputerResponseDTO (
    UUID id,
    String nameComputer,
    String operatingSystem, 
    String agentVersion
){
    public ComputerResponseDTO(Computer computer) {
        this(
            computer.getId(),
            computer.getNameComputer(),
            computer.getOperatingSystem(),
            computer.getAgentVersion()
        );
    }
}
