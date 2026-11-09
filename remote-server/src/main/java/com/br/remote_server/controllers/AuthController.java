package com.br.remote_server.controllers;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpHeaders;

import com.br.remote_server.dtos.users.AuthenticationRequestDTO;
import com.br.remote_server.dtos.users.AuthenticationResponseDTO;
import com.br.remote_server.dtos.users.UserRequestDTO;
import com.br.remote_server.dtos.users.UserResponseDTO;
import com.br.remote_server.exceptions.UnauthorizedUser;
import com.br.remote_server.infra.security.CookieFactory;
import com.br.remote_server.services.UserService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UserService userService;
    @Value("${api.security.token.refresh-expiration}")
    private int REFRESHTOKEN_EXPIRATION;
    @Autowired
    private CookieFactory cookieFactory;

    @PostMapping("/register")
    @Transactional
    public ResponseEntity<UserResponseDTO> createUser(@RequestBody @Valid UserRequestDTO data) {
        return ResponseEntity.ok(userService.createUser(data));
    }

    @PostMapping("/sign-in")
    public ResponseEntity<?> authenticate(@RequestBody @Valid AuthenticationRequestDTO data, HttpServletResponse response) {
        AuthenticationResponseDTO authResponse = userService.authenticate(data);
        ResponseCookie refreshCookie = cookieFactory.buildRefreshCookie(authResponse.refreshToken());

        response.setHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
        return ResponseEntity.ok(Map.of("accessToken", authResponse.accessToken()));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(@CookieValue(name = "refreshToken", required = false) String refreshToken) {
        if (refreshToken == null) {
            throw new UnauthorizedUser("Refresh token ausente");
        }
        try {
            AuthenticationResponseDTO response = userService.refreshToken(refreshToken);

            return ResponseEntity.ok(Map.of("accessToken", response.accessToken()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Refresh token inválido ou expirado.");
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> revokeToken(HttpServletResponse response) {
        ResponseCookie deleteCookie = cookieFactory.clearRefreshCookie();

        response.setHeader(HttpHeaders.SET_COOKIE, deleteCookie.toString());

        return ResponseEntity.noContent().build();
    }
}
