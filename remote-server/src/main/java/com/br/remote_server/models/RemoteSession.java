package com.br.remote_server.models;

import com.br.remote_server.enums.SessionEndReason;
import com.br.remote_server.enums.Status;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity(name = "RemoteSession")
@Table(name = "remote_sessions")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@EntityListeners(AuditingEntityListener.class)
public class RemoteSession {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "id", nullable = false, unique = true)
    private UUID sessionId;

    @ManyToOne
    @JoinColumn(name = "user_accessing_id")
    private User userAccessing;

    @ManyToOne
    @JoinColumn(name = "computer_id")
    private Computer computer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_session", nullable = false)
    private Status statusSession;

    @Column(name = "client_ip")
    private String clientIp;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason")
    private SessionEndReason endReason;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreatedDate
    private Instant createdAt;

    @Column(name = "updated_at")
    @LastModifiedDate
    private Instant updatedAt;

}
