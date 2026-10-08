package com.br.remote_server.websocket.registry;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component 
public class ComputerConnectionRegistry {
    
        private final Map<UUID, WebSocketSession> connections = new ConcurrentHashMap<>();

        public void register(UUID computerId, WebSocketSession session) {
            connections.put(computerId, session);
        }

        public WebSocketSession get(UUID computerId) {
            return connections.get(computerId);
        }

        public void remove(UUID computerId) {
            connections.remove(computerId);
        }

        public boolean isOnline(UUID computerId) {
            return connections.containsKey(computerId);
        }

        public Set<UUID> getOnlineComputers() {
            return connections.keySet();
        }

}
