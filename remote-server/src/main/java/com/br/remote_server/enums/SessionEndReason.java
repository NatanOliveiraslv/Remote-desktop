package com.br.remote_server.enums;

public enum SessionEndReason {

    USER_DISCONNECTED,     // Usuário encerrou voluntariamente
    AGENT_DISCONNECTED,    // Conexão do Agent caiu
    AGENT_REJECTED,        // Agent recusou a solicitação de conexão
    INACTIVITY,            // Timeout por inatividade
    ERROR                  // Erro de comunicação ou exceção inesperada

}