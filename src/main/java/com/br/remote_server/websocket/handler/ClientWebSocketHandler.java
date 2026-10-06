package com.br.remote_server.websocket.handler;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class ClientWebSocketHandler extends TextWebSocketHandler {

    @Override 
    public void afterConnectionEstablished(WebSocketSession session) {
        
        System.out.println("WebSocket connection established: " + session.getId());

    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        
        System.out.println("Received message: " + message.getPayload() + " from session: " + session.getId());

        session.sendMessage(new TextMessage("Message received: " + message.getPayload()));
    }

    @Override 
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        
        System.out.println("WebSocket connection closed: " + session.getId() + " with status: " + status);
    }
    
}
