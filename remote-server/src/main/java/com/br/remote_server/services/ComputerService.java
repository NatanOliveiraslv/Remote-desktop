package com.br.remote_server.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.br.remote_server.models.Computer;
import com.br.remote_server.repositorys.ComputerRepository;

@Service 
public class ComputerService {

    @Autowired 
    private ComputerRepository computerRepository;

    public Computer createComputer(Computer computer) {
        return computerRepository.save(computer);
    }
    
}
