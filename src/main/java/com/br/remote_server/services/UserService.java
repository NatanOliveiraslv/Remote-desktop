package com.br.remote_server.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.br.remote_server.models.User;
import com.br.remote_server.repositorys.UserRepository;

@Service 
public class UserService {

    @Autowired 
    private UserRepository userRepository;

    public User createUser(User user) {

        if (userRepository.existsByEmail(user.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        return userRepository.save(user);
    }

}