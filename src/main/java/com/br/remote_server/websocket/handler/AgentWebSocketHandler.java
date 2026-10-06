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
import com.br.remote_server.dtos.websocket.AgentRegisterMessage;

import tools.jackson.databind.ObjectMapper;

@Component
public class AgentWebSocketHandler extends TextWebSocketHandler {

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
        AgentRegisterMessage registerMessage = objectMapper.readValue(message.getPayload(), AgentRegisterMessage.class);

        if("AGENT_REGISTER".equals(registerMessage.type())) {
            UUID computerId = registerMessage.computerId();

            connectionRegistry.register(computerId, session);

            System.out.println("Computer registered: " + computerId + " from session: " + session.getId());

        }

        } catch (Exception e) {
            System.err.println("Error processing message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override 
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        
        System.out.println("WebSocket connection closed: " + session.getId() + " with status: " + status);
    }
    
}
