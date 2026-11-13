package com.br.remote_server.repositorys;

import com.br.remote_server.models.Computer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ComputerRepository extends JpaRepository<Computer, UUID> {

    List<Computer> findByOwnerUserId(UUID ownerUserId);
    boolean existsByIdAndOwnerUserId(UUID id, UUID ownerUserId);

    @Query("SELECT c.agentTokenHash FROM Computer c WHERE c.id = :id")
    Optional<String> findAgentTokenHashById(@Param("id") UUID id);
    
    Optional<Computer> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);
}
