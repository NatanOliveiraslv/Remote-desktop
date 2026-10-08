package com.br.remote_server.websocket.handler;

import java.io.IOException;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import com.br.remote_server.websocket.registry.ComputerConnectionRegistry;
import com.br.remote_server.dtos.websocket.ConnectComputerMessage;

import tools.jackson.databind.ObjectMapper;

@Component
public class ClientWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private ComputerConnectionRegistry connectionRegistry;
    @Autowired
    private ObjectMapper objectMapper;

    @Override 
    public void afterConnectionEstablished(WebSocketSession session) {
        
        System.out.println("WebSocket connection established: " + session.getId());

    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        
        try{

            ConnectComputerMessage connectMessage = objectMapper.readValue(message.getPayload(), ConnectComputerMessage.class);

            if("CONNECT".equals(connectMessage.type())) {
                connectToComputer(session, connectMessage.computerId());
            }

        } catch (Exception e){
            System.err.println("Error processing message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void connectToComputer(WebSocketSession clientSession, UUID computerId) throws IOException {

        WebSocketSession agentSession = connectionRegistry.get(computerId);

        if (agentSession == null || !agentSession.isOpen()){
            clientSession.sendMessage(new TextMessage("{\"type\":\"ERROR\",\"message\":\"Computer offline\"}"));
            return;
        }

        agentSession.sendMessage(new TextMessage("{\"type\":\"REMOTE_CONNECTION_REQUEST\"}"));

        clientSession.sendMessage(new TextMessage("{\"type\":\"CONNECTING\"}"));

    }

    @Override 
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        
        System.out.println("WebSocket connection closed: " + session.getId() + " with status: " + status);
    }
    
}
