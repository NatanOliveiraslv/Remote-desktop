package com.br.remote_server.services;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.br.remote_server.models.Computer;
import com.br.remote_server.models.User;
import com.br.remote_server.repositorys.ComputerRepository;
import com.br.remote_server.websocket.registry.ComputerConnectionRegistry;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import com.br.remote_server.dtos.computers.ComputerCreatedResponseDTO;
import com.br.remote_server.dtos.computers.ComputerRequestDTO;
import com.br.remote_server.dtos.computers.ComputerResponseDTO;
import com.br.remote_server.exceptions.ObjectNotFound;
import com.br.remote_server.infra.security.AgentTokenService;

@Service
public class ComputerService {

    @Autowired
    private ComputerRepository computerRepository;
    @Autowired
    private ComputerConnectionRegistry connectionRegistry;
    @Autowired
    private AgentTokenService agentTokenService;

    @Transactional
    public ComputerCreatedResponseDTO createComputer(ComputerRequestDTO request, User user) {
        String rawToken = agentTokenService.generateToken();
        String tokenHash = agentTokenService.hashToken(rawToken);

        Computer computer = new Computer(request, user, tokenHash);
        computerRepository.save(computer);

        return new ComputerCreatedResponseDTO(computer, rawToken);
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

    @Transactional(readOnly = true)
    public boolean validateAgentCredentials(UUID computerId, String rawToken) {
        // Busca só o hash — sem expor outros dados
        return computerRepository.findAgentTokenHashById(computerId)
                .map(storedHash -> agentTokenService.verifyToken(rawToken, storedHash))
                .orElse(false); // ID não existe
    }

    @Transactional
    public void updateLastSeenAt(UUID computerId) {
        computerRepository.findById(computerId).ifPresent(computer -> {
            computer.setLastSeenAt(Instant.now());
        });
    }

    @Transactional
    public ComputerCreatedResponseDTO rotateAgentToken(UUID computerId, User user) throws IOException {
        Computer computer = computerRepository.findByIdAndOwnerUserId(computerId, user.getId())
                .orElseThrow(() -> new ObjectNotFound("Computer not found"));

        WebSocketSession activeSession = connectionRegistry.get(computerId);
        if (activeSession != null && activeSession.isOpen()) {
            activeSession.close(CloseStatus.POLICY_VIOLATION);
            connectionRegistry.remove(computerId, activeSession);
        }

        String rawToken = agentTokenService.generateToken();
        String tokenHash = agentTokenService.hashToken(rawToken);
        computer.setAgentTokenHash(tokenHash);
        return new ComputerCreatedResponseDTO(computer, rawToken);
    }

}
