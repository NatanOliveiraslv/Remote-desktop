package com.br.remote_server.repositorys;

import com.br.remote_server.models.Computer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ComputerRepository extends JpaRepository<Computer, UUID> {
}
