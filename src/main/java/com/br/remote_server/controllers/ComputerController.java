package com.br.remote_server.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.br.remote_server.dtos.computers.ComputerRequestDTO;
import com.br.remote_server.dtos.computers.ComputerResponseDTO;
import com.br.remote_server.models.Computer;
import com.br.remote_server.services.ComputerService;
import com.br.remote_server.websocket.registry.ComputerConnectionRegistry;

import jakarta.validation.Valid;

@RestController 
@RequestMapping("/computers")
public class ComputerController {

    @Autowired 
    private ComputerService computerService;
    @Autowired 
    private ComputerConnectionRegistry connectionRegistry;

    @PostMapping("/create")
    @Transactional 
    public ResponseEntity<ComputerResponseDTO> createComputer(@RequestBody @Valid ComputerRequestDTO requestDTO) {
        Computer computer = computerService.createComputer(new Computer(requestDTO));
        ComputerResponseDTO responseDTO = new ComputerResponseDTO(computer);
        return ResponseEntity.ok(responseDTO);
    }

    @GetMapping("/status/online")
    public ResponseEntity<?> getOnlineComputers() {
        return ResponseEntity.ok(connectionRegistry.getOnlineComputers());
    }

}
