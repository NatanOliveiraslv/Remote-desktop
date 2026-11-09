package com.br.remote_server.dtos.users;

public record AuthenticationResponseDTO(
        String accessToken,
        String refreshToken
) {
}