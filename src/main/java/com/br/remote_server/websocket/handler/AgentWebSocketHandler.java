package com.br.remote_server.websocket.handler;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import com.br.remote_server.websocket.registry.ComputerConnectionRegistry;
import com.br.remote_server.dtos.websocket.AgentRegisterMessage;
import com.br.remote_server.dtos.websocket.AgentConnectionAccepted;
import com.br.remote_server.websocket.registry.RemoteSessionRegistry;
import com.br.remote_server.websocket.registry.ActiveRemoteSession;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class AgentWebSocketHandler extends TextWebSocketHandler {

    @Autowired 
    private ComputerConnectionRegistry connectionRegistry;
    @Autowired 
    private ObjectMapper objectMapper;
    @Autowired 
    private RemoteSessionRegistry remoteSessionRegistry;

    @Override 
    public void afterConnectionEstablished(WebSocketSession session) {
        
        System.out.println("WebSocket connection established: " + session.getId());

    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        
        try{
            JsonNode json = objectMapper.readTree(message.getPayload());

            String type = json.path("type").asText();

            switch (type) {

                case "AGENT_REGISTER" -> {
                    
                    AgentRegisterMessage registerMessage = objectMapper.readValue( message.getPayload(), AgentRegisterMessage.class);

                    UUID computerId = registerMessage.computerId();

                    connectionRegistry.register(computerId, session);

                    session.getAttributes().put("computerId", computerId);

                    System.out.println("Computer registered: " + computerId);

                }

                case "CONNECTION_ACCEPTED" -> {

                    AgentConnectionAccepted connectionAccepted = objectMapper.readValue( message.getPayload(), AgentConnectionAccepted.class);
                    
                    if(connectionAccepted.remoteSessionId().isBlank()) {
                        System.err.println("Missing remoteSessionId in Agent response");
                        return;
                    }

                    UUID remoteSessionId = UUID.fromString(connectionAccepted.remoteSessionId());

                    ActiveRemoteSession remoteSession = remoteSessionRegistry.get(remoteSessionId);

                    if (remoteSession == null) {
                        System.err.println("Remote session not found: " + remoteSessionId);
                        return;
                    }

                     // Confirma que a resposta veio do Agent esperado.
                    if (!remoteSession.agentSession().getId().equals(session.getId())) {
                        System.err.println("Agent session mismatch for remote session: " + remoteSessionId);
                        return;
                    }

                    WebSocketSession clientSession = remoteSession.clientSession();

                    if(!clientSession.isOpen()) {
                        remoteSessionRegistry.remove(remoteSessionId);
                        return;
                    }

                    Map<String, Object> response = Map.of(
                        "type", "CONNECTION_ACCEPTED",
                        "remoteSessionId", remoteSessionId.toString(),
                        "computerId", remoteSession.computerId().toString()
                    );

                    clientSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));

                    System.out.println("Connection accepted and forwarded to Client: " + remoteSessionId);

                }

                default -> {
                    System.err.println("Unknown message type: " + type);
                }
            }

        } catch (Exception e) {
            System.err.println("Error processing message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override 
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {

        Object value = session.getAttributes().get("computerId");

        if(value instanceof UUID computerId) {
            connectionRegistry.remove(computerId, session);
        }

        remoteSessionRegistry.removeByAgentSession(session);
        
        System.out.println("Agent disconnected: " + session.getId());
    }
    
}
