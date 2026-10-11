package com.br.remote_server.websocket.registry;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component 
public class RemoteSessionRegistry {

    private final Map<UUID, ActiveRemoteSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID,UUID> sessionIdByComputer = new ConcurrentHashMap<>();

    public synchronized ActiveRemoteSession create(UUID computerId, WebSocketSession clientSession, WebSocketSession agentSession) {

        if(sessionIdByComputer.containsKey(computerId)) {
            throw new IllegalStateException("A session for this computer is already active.");
        }

        ActiveRemoteSession remoteSession = new ActiveRemoteSession(UUID.randomUUID(), computerId, clientSession, agentSession);

        sessions.put(remoteSession.id(), remoteSession);
        sessionIdByComputer.put(computerId, remoteSession.id());

        return remoteSession;
    }

    public ActiveRemoteSession get(UUID sessionId) {
        return sessions.get(sessionId);
    }

    public synchronized void remove(UUID sessionId) {

        ActiveRemoteSession remoteSession = sessions.remove(sessionId);

        if(remoteSession != null) {
            sessionIdByComputer.remove(remoteSession.computerId(), sessionId);
        }

    }

    public synchronized void removeByClientSession(WebSocketSession clientSession) {

        for(ActiveRemoteSession remoteSession : new ArrayList<>(sessions.values())) {

            if(remoteSession.clientSession().getId().equals(clientSession.getId())) {
                remove(remoteSession.id());
            }
        }
    }

    public synchronized void removeByAgentSession(WebSocketSession agentSession) {

        for(ActiveRemoteSession remoteSession : new ArrayList<>(sessions.values())) {

            if(remoteSession.agentSession().getId().equals(agentSession.getId())) {
                remove(remoteSession.id());
            }
        }
    }
    
}