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

import com.br.remote_server.websocket.registry.ActiveRemoteSession;
import com.br.remote_server.websocket.registry.ComputerConnectionRegistry;
import com.br.remote_server.websocket.registry.RemoteSessionRegistry;
import com.br.remote_server.dtos.websocket.ConnectComputerMessage;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class ClientWebSocketHandler extends TextWebSocketHandler {

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

                case "CONNECT" -> {
                    ConnectComputerMessage connectMessage = objectMapper.readValue(message.getPayload(), ConnectComputerMessage.class);
                    
                    connectToComputer(session, connectMessage.computerId());
                
                }

                default -> {
                    forwardToAgent(session, json);
                }
            }

        } catch (Exception e){
            System.err.println("Error processing Client message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void forwardToAgent(WebSocketSession clientSession, JsonNode json) throws IOException {
        
        String sessionIdValue = json.path("remoteSessionId").asText();

        if (sessionIdValue.isBlank()) {
            sendJson(clientSession, Map.of(
                "type", "ERROR",
                "message", "Missing remoteSessionId"
            ));
            return;
        }

        UUID remoteSessionId = UUID.fromString(sessionIdValue);

        ActiveRemoteSession remoteSession = remoteSessionRegistry.get(remoteSessionId);

        if (remoteSession == null
                || !remoteSession.clientSession().getId().equals(clientSession.getId())
                || !remoteSessionRegistry.isActive(remoteSessionId)) {
                    
            sendJson(clientSession, Map.of(
                "type", "ERROR",
                "message", "Invalid or inactive remote session"
            ));
            return;
        }

        WebSocketSession agentSession = remoteSession.agentSession();

        if (!agentSession.isOpen()) {
            sendJson(clientSession, Map.of(
                "type", "ERROR",
                "message", "Agent disconnected"
            ));
            return;
        }

        agentSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(json)));
    
    }

    private void connectToComputer(WebSocketSession clientSession, UUID computerId) throws IOException {

        WebSocketSession agentSession = connectionRegistry.get(computerId);

        if (agentSession == null || !agentSession.isOpen()){
            clientSession.sendMessage(new TextMessage("{\"type\":\"ERROR\",\"message\":\"Computer offline\"}"));
            return;
        }

        ActiveRemoteSession remoteSession;

        try {
            remoteSession = remoteSessionRegistry.create(computerId, clientSession, agentSession);
        } catch (IllegalStateException e) {
            sendJson(clientSession, Map.of(
                "type", "ERROR",
                "message", "Computer already has an active session"
            ));
            return;
        }

        try {
            // Primeiro, avisa o Client que a solicitação está em andamento
            sendJson(clientSession, Map.of(
                "type", "CONNECTING",
                "remoteSessionId", remoteSession.id().toString()
            ));

            // Depois, encaminha o pedido ao Agent.
            sendJson(agentSession, Map.of(
                "type", "REMOTE_CONNECTION_REQUEST",
                "remoteSessionId", remoteSession.id().toString(),
                "computerId", computerId.toString()
            ));
        } catch (IOException e) {
            // Se houver um erro ao enviar a mensagem para o Agent, remove a sessão remota e notifica o Client.
            remoteSessionRegistry.remove(remoteSession.id());
            
            if (clientSession.isOpen()) {
                sendJson(clientSession, Map.of(
                    "type", "ERROR",
                    "message", "Failed to initiate remote session"
                ));
            }

            throw e;
        }

    }

    private void sendJson(WebSocketSession session, Map<String, Object> data) throws IOException {
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(data)));
    }

    @Override 
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        
        remoteSessionRegistry.removeByClientSession(session);
        
        System.out.println("WebSocket connection closed: " + session.getId() + " with status: " + status);
    }
    
}
