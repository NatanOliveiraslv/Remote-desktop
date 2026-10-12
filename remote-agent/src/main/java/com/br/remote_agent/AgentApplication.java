package com.br.remote_agent;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import com.br.remote_agent.websocket.AgentWebSocketHandler;

public class AgentApplication {

    public static void main(String[] args)
            throws InterruptedException, Exception {

        AgentWebSocketHandler handler = new AgentWebSocketHandler(
                URI.create(
                        "ws://localhost:8080/api/v1/ws/agent"),
                UUID.fromString(
                        "550e8400-e29b-41d4-a716-446655440000"));

        handler.connect();

        // Mantém a aplicação em execução durante o teste.
        new CountDownLatch(1).await();
    }
}