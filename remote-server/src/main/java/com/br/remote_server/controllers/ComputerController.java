package com.br.remote_server.controllers;

import java.util.List;

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

import jakarta.validation.Valid;

@RestController
@RequestMapping("/computers")
public class ComputerController {

    @Autowired
    private ComputerService computerService;

    @PostMapping("/create")
    @Transactional
    public ResponseEntity<ComputerResponseDTO> createComputer(@RequestBody @Valid ComputerRequestDTO request) {
        Computer computer = computerService.createComputer(new Computer(request));
        ComputerResponseDTO responseDTO = new ComputerResponseDTO(computer, false);
        return ResponseEntity.ok(responseDTO);
    }

    @GetMapping
    public List<ComputerResponseDTO> findAll() {
        return computerService.findAll();
    }

}
