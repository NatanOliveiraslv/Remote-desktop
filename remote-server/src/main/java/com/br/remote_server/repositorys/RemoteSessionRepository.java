package com.br.remote_server.repositorys;

import com.br.remote_server.models.RemoteSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RemoteSessionRepository extends JpaRepository<RemoteSession, UUID> {
}
