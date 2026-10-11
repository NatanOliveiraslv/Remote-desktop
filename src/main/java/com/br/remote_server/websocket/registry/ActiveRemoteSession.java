package com.br.remote_server.websocket.registry;

import java.util.UUID;

import org.springframework.web.socket.WebSocketSession;

public record ActiveRemoteSession (
    UUID id,
    UUID computerId,
    WebSocketSession clientSession,
    WebSocketSession agentSession
){
    
}
