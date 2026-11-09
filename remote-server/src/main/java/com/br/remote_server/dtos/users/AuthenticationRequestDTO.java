package com.br.remote_server.dtos.users;

public record AuthenticationRequestDTO(
        String username,
        String password
) {
}