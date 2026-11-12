package com.br.remote_server.dtos.users;

import com.br.remote_server.models.User;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;

public record UserRequestDTO (
    
    @NotBlank
    @Email
    String email,

    @NotBlank(message = "A senha é obrigatória")
    @Size(min = 8, message = "A senha deve ter no mínimo 8 caracteres")
    @Pattern(regexp = ".*[A-Z].*", message = "A senha deve conter ao menos uma letra maiúscula")
    @Pattern(regexp = ".*[a-z].*", message = "A senha deve conter ao menos uma letra minúscula")
    @Pattern(regexp = ".*\\d.*", message = "A senha deve conter ao menos um número")
    @Pattern(regexp = ".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*", message = "A senha deve conter ao menos um caractere especial")
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
