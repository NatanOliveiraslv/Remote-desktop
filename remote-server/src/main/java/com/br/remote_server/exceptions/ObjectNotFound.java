package com.br.remote_server.exceptions;

public class ObjectNotFound extends RuntimeException {
    public ObjectNotFound() {
        super("Id não encontrado");
    }

    public ObjectNotFound(String mensagem) {
        super(mensagem);
    }
}