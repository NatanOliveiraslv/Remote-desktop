package com.br.remote_server.infra.security;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

@Component
public class AgentTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // Gera um token aleatório de 32 bytes, retornado como hex (64 chars). */
    public String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /// Retorna o SHA-256 do token (hex).
    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(HexFormat.of().parseHex(rawToken));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    // Compara dois hashes em tempo constante para evitar timing attacks
    public boolean verifyToken(String rawToken, String storedHash) {
        String incomingHash = hashToken(rawToken);
        // MessageDigest.isEqual faz comparação byte a byte em tempo constante
        return MessageDigest.isEqual(
                HexFormat.of().parseHex(incomingHash),
                HexFormat.of().parseHex(storedHash));
    }

}
