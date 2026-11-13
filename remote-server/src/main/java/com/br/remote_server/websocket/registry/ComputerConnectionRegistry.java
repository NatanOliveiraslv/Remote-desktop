package com.br.remote_server.websocket.registry;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

@Component 
public class ComputerConnectionRegistry {
    
        private final Map<UUID, WebSocketSession> connections = new ConcurrentHashMap<>();

        public void register(UUID computerId, WebSocketSession session) {
            WebSocketSession oldSession = connections.put(computerId, session);

            // Se já houver uma sessão existente para o mesmo computador, fecha a sessão antiga
            if (oldSession != null && oldSession.isOpen()) {
                try {
                    oldSession.close(CloseStatus.POLICY_VIOLATION);
                    System.out.println("Replaced old Agent session for computer: " + computerId);
                } catch (IOException e) {
                    System.err.println("Error closing old session: " + e.getMessage());
                }
            }
        }

        public WebSocketSession get(UUID computerId) {
            return connections.get(computerId);
        }

        public void remove(UUID computerId, WebSocketSession session) {
            connections.remove(computerId, session);
        }

        public boolean isOnline(UUID computerId) {
            return connections.containsKey(computerId);
        }

        public Set<UUID> getOnlineComputers() {
            return connections.keySet();
        }

}
