package com.br.remote_server.infra.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.br.remote_server.websocket.handler.AgentWebSocketHandler;
import com.br.remote_server.websocket.handler.ClientWebSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer{

    @Autowired
    private AgentWebSocketHandler agentWebSocketHandler;
    @Autowired
    private ClientWebSocketHandler clientWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry  registry) {
        registry.addHandler(agentWebSocketHandler, "/ws/agent").setAllowedOrigins("*");
        registry.addHandler(clientWebSocketHandler, "/ws/client").setAllowedOrigins("*");
    }
    
}
