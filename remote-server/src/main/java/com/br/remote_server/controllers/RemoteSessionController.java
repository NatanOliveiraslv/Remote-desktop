package com.br.remote_server.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.br.remote_server.dtos.remoteSessions.RemoteSessionResponseDTO;
import com.br.remote_server.models.User;
import com.br.remote_server.services.RemoteSessionService;

@RestController
@RequestMapping("/remote-sessions")
public class RemoteSessionController {
    
    @Autowired
    private RemoteSessionService remoteSessionService;

    @GetMapping
    public ResponseEntity<Page<RemoteSessionResponseDTO>> listSessions(
        @AuthenticationPrincipal User user, 
        @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        
        Page<RemoteSessionResponseDTO> sessions = remoteSessionService.listUserSessions(user, pageable);
        return ResponseEntity.ok(sessions);
    }

}
