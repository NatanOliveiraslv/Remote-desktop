package com.br.remote_server.infra.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

import com.br.remote_server.exceptions.UnauthorizedUser;
import com.br.remote_server.repositorys.UserRepository;
import com.br.remote_server.services.CustomOAuth2User;

@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Autowired
    private TokenService tokenService;
    @Autowired
    private UserRepository userRepository;
    @Value("${api.security.token.refresh-expiration}")
    private int REFRESHTOKEN_EXPIRATION;
    @Value("${frontend.url}")
    private String FRONTEND_URL;
    @Autowired
    private CookieFactory cookieFactory;


    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        Object principal = authentication.getPrincipal();
        String username;

        if (principal instanceof CustomOAuth2User customUser) {
            username = customUser.getUsername();
        } else if (principal instanceof DefaultOidcUser oidcUser) {
            username = oidcUser.getEmail(); // ou getAttribute("email")
        } else {
            throw new IllegalStateException("Tipo de usuário não suportado: " + principal.getClass());
        }

        userRepository.findByUsername(username).orElseThrow(() -> new UnauthorizedUser("Usário não autorizado"));

        String token = tokenService.generateToken(username);
        String refreshToken = tokenService.generateRefreshToken(username);

        ResponseCookie refreshCookie = cookieFactory.buildRefreshCookie(refreshToken);

        response.setHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        String redirectUrl = FRONTEND_URL + "/login-success?accessToken=" + token;

        response.sendRedirect(redirectUrl);
    }
}