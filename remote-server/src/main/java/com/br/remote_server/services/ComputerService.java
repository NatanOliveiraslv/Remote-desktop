package com.br.remote_server.services;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.br.remote_server.models.Computer;
import com.br.remote_server.models.User;
import com.br.remote_server.repositorys.ComputerRepository;
import com.br.remote_server.websocket.registry.ComputerConnectionRegistry;

import org.springframework.transaction.annotation.Transactional;

import com.br.remote_server.dtos.computers.ComputerRequestDTO;
import com.br.remote_server.dtos.computers.ComputerResponseDTO;

@Service
public class ComputerService {

    @Autowired
    private ComputerRepository computerRepository;
    @Autowired
    private ComputerConnectionRegistry connectionRegistry;

    @Transactional
    public Computer createComputer(ComputerRequestDTO request, User user) {
        Computer computer = new Computer(request, user);
        return computerRepository.save(computer);
    }

    @Transactional(readOnly = true)
    public List<ComputerResponseDTO> listComputer(User user) {
        return computerRepository.findByOwnerUserId(user.getId())
                .stream()
                .map(computer -> new ComputerResponseDTO(
                        computer, connectionRegistry.isOnline(computer.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean existsById(UUID id) {
        return computerRepository.existsById(id);
    }

    @Transactional(readOnly = true)
    public boolean canAccess(UUID computerId, UUID userId) {
        return computerRepository.existsByIdAndOwnerUserId(computerId, userId);
    }

}
