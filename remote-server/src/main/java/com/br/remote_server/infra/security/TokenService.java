package com.br.remote_server.infra.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class TokenService {

    @Value("${api.security.token.secret}")
    private String SECRET_KEY;
    @Value("${api.security.token.issuer}")
    private String ISSUER;
    @Value("${api.security.token.expiration}")
    private int TOKEN_EXPIRATION;
    @Value("${api.security.token.refresh-expiration}")
    private int REFRESHTOKEN_EXPIRATION;

    private enum TokenType {
        ACCESS, REFRESH
    }

    private static final String TYPE_CLAIM = "type";

    private String buildToken(String username, int seconds, TokenType type) {
        try {
            return JWT.create()
                    .withIssuer(ISSUER) // Define o emissor do token
                    .withIssuedAt(Instant.now()) // Define a data de emissão do token
                    .withExpiresAt(Instant.now().plusSeconds(seconds)) // Define a data de expiração do token
                    .withSubject(username) // Define o assunto do token (neste caso, o nome de usuário)
                    .withClaim(TYPE_CLAIM, type.name()) // Adiciona uma reivindicação personalizada para indicar o tipo de token
                    .sign(Algorithm.HMAC256(SECRET_KEY)); // Assina o token usando o algoritmo HMAC SHA256 passandoo a chave secreta
        } catch (JWTCreationException exception) {
            throw new JWTCreationException("Erro ao gerar token.", exception);
        }
    }

    private String verify(String token, TokenType type) {
    try {
        return JWT.require(Algorithm.HMAC256(SECRET_KEY))
                .withIssuer(ISSUER)
                .withClaim(TYPE_CLAIM, type.name())
                .build()
                .verify(token)
                .getSubject();
    } catch (JWTVerificationException e) {
        throw new RuntimeException("Token inválido ou expirado", e);
    }
}
    public String generateToken(String username) {
        return buildToken(username, TOKEN_EXPIRATION, TokenType.ACCESS);
    }

    public String generateRefreshToken(String username) {
        return buildToken(username, REFRESHTOKEN_EXPIRATION, TokenType.REFRESH);
    }

    public String getSubject(String token) {
        return verify(token, TokenType.ACCESS);
    }

    public String getRefreshSubject(String token) {
        return verify(token, TokenType.REFRESH);
    }

}