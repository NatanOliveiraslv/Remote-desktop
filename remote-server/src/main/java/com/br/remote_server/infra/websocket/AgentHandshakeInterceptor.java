package com.br.remote_server.infra.websocket;

import com.br.remote_server.services.ComputerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.UUID;

@Component
public class AgentHandshakeInterceptor implements HandshakeInterceptor {

    @Autowired
    private ComputerService computerService;
    @Override
    
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) {
        var params = UriComponentsBuilder
                .fromUri(request.getURI())
                .build()
                .getQueryParams();
        String computerIdParam = params.getFirst("computerId");
        String token = params.getFirst("agentToken");

        if (computerIdParam == null || token == null) {
            return false; // rejeita — nem abre a conexão WS
        }

        try {
            UUID computerId = UUID.fromString(computerIdParam);
            if (!computerService.validateAgentCredentials(computerId, token)) {
                return false; // token/id inválido — rejeita
            }
            // Passa o computerId para os atributos da sessão WS
            attributes.put("computerId", computerId);
            return true;
        } catch (IllegalArgumentException e) {
            return false; // UUID malformado — rejeita
     
        }
    }
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
        // Não é necessário fazer nada aqui
    }
}