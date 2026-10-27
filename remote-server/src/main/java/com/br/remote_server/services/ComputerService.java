package com.br.remote_server.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.br.remote_server.models.Computer;
import com.br.remote_server.repositorys.ComputerRepository;
import com.br.remote_server.websocket.registry.ComputerConnectionRegistry;
import com.br.remote_server.dtos.computers.ComputerResponseDTO;

@Service
public class ComputerService {

    @Autowired
    private ComputerRepository computerRepository;
    @Autowired
    private ComputerConnectionRegistry connectionRegistry;

    public Computer createComputer(Computer computer) {
        return computerRepository.save(computer);
    }

    public List<ComputerResponseDTO> findAll() {
        return computerRepository.findAll()
                .stream()
                .map(computer -> new ComputerResponseDTO(
                        computer.getId(),
                        computer.getNameComputer(),
                        computer.getOperatingSystem(),
                        computer.getAgentVersion(),
                        connectionRegistry.isOnline(computer.getId())))
                .toList();
    }

}
