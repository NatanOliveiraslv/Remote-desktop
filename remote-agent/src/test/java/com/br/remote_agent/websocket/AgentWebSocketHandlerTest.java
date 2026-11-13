package com.br.remote_agent.websocket;

import com.br.remote_agent.security.AgentCredentials;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgentWebSocketHandlerTest {

    @Test
    void testHandlerInitializesAndHoldsCredentials() throws Exception {
        UUID computerId = UUID.randomUUID();
        String token = "sample-token-abc";
        URI uri = URI.create("ws://localhost:8080/api/v1/ws/agent");

        AgentWebSocketHandler handler = new AgentWebSocketHandler(uri, computerId, token);
        assertEquals(computerId, handler.getCredentials().computerId());
        assertEquals(token, handler.getCredentials().agentToken());
    }
}
