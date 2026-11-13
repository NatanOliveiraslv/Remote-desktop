package com.br.remote_server.controllers;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.br.remote_server.dtos.computers.ComputerCreatedResponseDTO;
import com.br.remote_server.dtos.computers.ComputerRequestDTO;
import com.br.remote_server.dtos.computers.ComputerResponseDTO;
import com.br.remote_server.models.User;
import com.br.remote_server.services.ComputerService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/computers")
public class ComputerController {

    @Autowired
    private ComputerService computerService;

    @PostMapping("/create")
    public ResponseEntity<ComputerCreatedResponseDTO> createComputer(@RequestBody @Valid ComputerRequestDTO request, @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(computerService.createComputer(request, user));
    }

    @GetMapping
    public List<ComputerResponseDTO> listComputer(@AuthenticationPrincipal User user) {
        return computerService.listComputer(user);
    }

    @PostMapping("/{id}/rotate-token")
    public ResponseEntity<ComputerCreatedResponseDTO> rotateAgentToken(@PathVariable("id") UUID computerId, @AuthenticationPrincipal User user) throws IOException {
        return ResponseEntity.ok(computerService.rotateAgentToken(computerId, user));
    }

}
