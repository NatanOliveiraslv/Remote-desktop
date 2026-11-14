package com.br.remote_server.dtos.remoteSessions;

import java.time.Instant;
import java.util.UUID;

import com.br.remote_server.enums.SessionEndReason;
import com.br.remote_server.models.RemoteSession;
import com.br.remote_server.enums.Status;

public record RemoteSessionResponseDTO (

    UUID sessionId,
    UUID computerId,
    String computerName,
    Status status,
    String clientIp,
    SessionEndReason endReason,
    Long durationSeconds,
    Instant startedAt,
    Instant endedAt,
    Instant createdAt

) {
    public RemoteSessionResponseDTO(RemoteSession session) {
        this(
            session.getSessionId(),
            session.getComputer() != null ? session.getComputer().getId() : null,
            session.getComputer() != null ? session.getComputer().getNameComputer() : null,
            session.getStatusSession(),
            session.getClientIp(),
            session.getEndReason(),
            session.getDurationSeconds(),
            session.getStartedAt(),
            session.getEndedAt(),
            session.getCreatedAt()
        );
    }
}