package com.br.remote_server.models;

//PK id : UUID
//name : VARCHAR
//operatingSystem : VARCHAR
//agentVersion : VARCHAR
//lastSeenAt : TIMESTAMP
//FK user_id : UUID
//updatedAt : TIMESTAMP
//createdAt : TIMESTAMP

import com.br.remote_server.enums.Provider;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity(name = "Computer")
@Table(name = "copmuters")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@EntityListeners(AuditingEntityListener.class)
public class Computer {

    @Id
    @EqualsAndHashCode.Include
    @Column(nullable = false, unique = true)
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, name = "name_computer")
    private String nameComputer;

    @Column(nullable = false, name = "operating_system")
    private String operatingSystem;

    @Column(nullable = false, name = "agent_version")
    private String agentVersion;

    @Column(name = "last_seen_at")
    @CreatedDate
    private Instant lastSeenAt;

    @ManyToOne
    @JoinColumn(name = "ownerUser_id")
    private User ownerUser;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreatedDate
    private Instant createdAt;

    @Column(name = "updated_at")
    @LastModifiedDate
    private Instant updatedAt;

}
