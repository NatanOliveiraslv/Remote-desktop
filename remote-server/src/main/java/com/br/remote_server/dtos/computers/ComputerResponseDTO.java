package com.br.remote_server.dtos.computers;

import java.util.UUID;

import com.br.remote_server.models.Computer;

public record ComputerResponseDTO (
    UUID id,
    String nameComputer,
    String operatingSystem, 
    String agentVersion,
    boolean online
){
    public ComputerResponseDTO(Computer computer, boolean online) {
        this(
            computer.getId(),
            computer.getNameComputer(),
            computer.getOperatingSystem(),
            computer.getAgentVersion(),
            online
        );
    }
}
