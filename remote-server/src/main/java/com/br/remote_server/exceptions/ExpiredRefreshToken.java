package com.br.remote_server.exceptions;

public class ExpiredRefreshToken extends RuntimeException {
    public ExpiredRefreshToken() {
        super("Token de atualização inválido ou expirado");
    }

    public ExpiredRefreshToken(String message) {
        super(message);
    }
}