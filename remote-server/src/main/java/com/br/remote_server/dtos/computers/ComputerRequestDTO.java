package com.br.remote_server.dtos.computers;

import com.br.remote_server.models.Computer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ComputerRequestDTO (

    @NotBlank @Size(max = 100) String nameComputer,
    @NotBlank @Size(max = 100) String operatingSystem,
    @NotBlank @Size(max = 50) String agentVersion
){
    public ComputerRequestDTO(Computer computer) {
        this(
            computer.getNameComputer(), 
            computer.getOperatingSystem(), 
            computer.getAgentVersion()
        );
    }
}
