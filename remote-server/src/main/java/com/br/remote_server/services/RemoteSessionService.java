package com.br.remote_server.services;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.br.remote_server.dtos.remoteSessions.RemoteSessionResponseDTO;
import com.br.remote_server.enums.SessionEndReason;
import com.br.remote_server.enums.Status;
import com.br.remote_server.models.Computer;
import com.br.remote_server.models.RemoteSession;
import com.br.remote_server.models.User;
import com.br.remote_server.repositorys.RemoteSessionRepository;

import org.springframework.transaction.annotation.Transactional;

@Service
public class RemoteSessionService {

    @Autowired
    private RemoteSessionRepository remoteSessionRepository;

    // 1. Início da solicitação (REQUESTED)
    @Transactional
    public RemoteSession startSessionRequest(UUID sessionId, User user, Computer computer, String clientIp) {
        return remoteSessionRepository.save(RemoteSession.builder()
                .sessionId(sessionId)
                .userAccessing(user)
                .computer(computer)
                .clientIp(clientIp)
                .statusSession(Status.REQUESTED)
                .build());
    }

    // 2. Aceite do Agent (ACTIVE)
    @Transactional
    public void markAsAccepted(UUID sessionId) {
        remoteSessionRepository.findById(sessionId).ifPresent(session -> {
            session.setStatusSession(Status.ACTIVE);
            session.setStartedAt(Instant.now());
        });
    }

    // 3. Recusa do Agent (REJECTED)
    @Transactional
    public void markAsRejected(UUID sessionId) {
        remoteSessionRepository.findById(sessionId).ifPresent(session -> {
            session.setStatusSession(Status.REJECTED);
            session.setEndReason(SessionEndReason.AGENT_REJECTED);
            session.setEndedAt(Instant.now());
            session.setDurationSeconds(0L);
        });
    }

    // 4. Fim da sessão com cálculo de duração
    @Transactional
    public void endSession(UUID sessionId, SessionEndReason reason) {
        remoteSessionRepository.findById(sessionId).ifPresent(session -> {
            Instant end = Instant.now();
            session.setEndedAt(end);
            session.setEndReason(reason);
            session.setStatusSession(reason == SessionEndReason.ERROR ? Status.FAILED : Status.FINISHED);
            if (session.getStartedAt() != null) {
                session.setDurationSeconds(Duration.between(session.getStartedAt(), end).toSeconds());
            } else {
                session.setDurationSeconds(0L);
            }
        });
    }

    @Transactional(readOnly = true)
    public Page<RemoteSessionResponseDTO> listUserSessions(User user, Pageable pageable) {
        return remoteSessionRepository
                .findByUserAccessingId(user.getId(), pageable)
                .map(RemoteSessionResponseDTO::new);
    }

}
