package com.br.remote_server.dtos.users;

import java.util.UUID;

import com.br.remote_server.models.User;

public record UserResponseDTO (
    UUID id,
    String email,
    String firstName,
    String lastName
) {
    public UserResponseDTO(User user) {
        this(
            user.getId(), 
            user.getEmail(), 
            user.getFirstName(), 
            user.getLastName()
        );
    }
}

