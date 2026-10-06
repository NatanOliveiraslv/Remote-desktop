package com.br.remote_server.dtos.computers;

import com.br.remote_server.models.Computer;

public record ComputerRequestDTO (

    String nameComputer,
    String operatingSystem, 
    String agentVersion
){
    public ComputerRequestDTO(Computer computer) {
        this(
            computer.getNameComputer(), 
            computer.getOperatingSystem(), 
            computer.getAgentVersion()
        );
    }
}
