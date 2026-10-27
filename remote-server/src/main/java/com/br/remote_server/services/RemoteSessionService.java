package com.br.remote_server.services;

import org.springframework.beans.factory.annotation.Autowired;

import com.br.remote_server.models.RemoteSession;
import com.br.remote_server.repositorys.RemoteSessionRepository;

public class RemoteSessionService {

    @Autowired 
    private RemoteSessionRepository remoteSessionRepository;

    public RemoteSession regiterRemoteSession(RemoteSession remoteSession) {
        return remoteSessionRepository.save(remoteSession);
    }
    
}
