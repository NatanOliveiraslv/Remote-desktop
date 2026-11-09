package com.br.remote_server.exceptions;

public class UnauthorizedUser extends RuntimeException {

    public UnauthorizedUser() {
        super("Usuário não possui autorização");
    }

    public UnauthorizedUser(String message) {
        super(message);
    }
}