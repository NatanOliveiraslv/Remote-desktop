package com.br.remote_server.dtos.users;

import com.br.remote_server.models.User;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;

public record UserRequestDTO (
    
    @NotBlank
    @Email
    String email,
    @NotBlank
    String password,
    @NotBlank
    String firstName,
    @NotBlank
    String lastName
) {
    public UserRequestDTO(User user) {
        this(
            user.getEmail(), 
            user.getPassword(), 
            user.getFirstName(), 
            user.getLastName()
        );
    }
}
