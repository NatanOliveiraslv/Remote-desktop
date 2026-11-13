package com.br.remote_agent.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SecureCredentialStoreTest {

    @Test
    void testCredentialsValidation() {
        UUID computerId = UUID.randomUUID();
        String token = "my-secret-token-1234567890";

        AgentCredentials creds = new AgentCredentials(computerId, token);
        assertEquals(computerId, creds.computerId());
        assertEquals(token, creds.agentToken());
        assertTrue(creds.maskedToken().contains("..."));
        assertFalse(creds.toString().contains(token)); // Ensure token is masked in toString()

        assertThrows(NullPointerException.class, () -> new AgentCredentials(null, token));
        assertThrows(NullPointerException.class, () -> new AgentCredentials(computerId, null));
        assertThrows(IllegalArgumentException.class, () -> new AgentCredentials(computerId, "   "));
    }

    @Test
    void testSaveAndLoadEncryptedCredentials(@TempDir Path tempDir) throws IOException {
        SecureCredentialStore store = new SecureCredentialStore();
        Path credentialsPath = tempDir.resolve(".agent-credentials");

        UUID computerId = UUID.randomUUID();
        String token = "7f8b9c0d1e2f3a4b5c6d7e8f90123456789abcdef0123456789abcdef0123456";

        AgentCredentials original = new AgentCredentials(computerId, token);
        store.saveCredentials(credentialsPath, original);

        assertTrue(Files.exists(credentialsPath));
        byte[] encryptedBytes = Files.readAllBytes(credentialsPath);
        // The raw token must NOT be stored in plaintext in the file
        String rawContent = new String(encryptedBytes);
        assertFalse(rawContent.contains(token), "Credentials file must encrypt the token at rest!");

        // Loading should decrypt and verify integrity
        AgentCredentials loaded = store.loadCredentials(credentialsPath);
        assertEquals(original.computerId(), loaded.computerId());
        assertEquals(original.agentToken(), loaded.agentToken());
    }

    @Test
    void testResolveFromArgs(@TempDir Path tempDir) throws IOException {
        SecureCredentialStore store = new SecureCredentialStore();
        Path credentialsPath = tempDir.resolve(".agent-credentials");

        UUID computerId = UUID.randomUUID();
        String token = "arg-token-xyz";

        String[] args = new String[]{"--server-uri", "ws://localhost:8080", "--computer-id", computerId.toString(), "--token", token};
        AgentCredentials resolved = store.resolveCredentials(args, credentialsPath);

        assertEquals(computerId, resolved.computerId());
        assertEquals(token, resolved.agentToken());
        // Should have been persisted automatically
        assertTrue(Files.exists(credentialsPath));

        AgentCredentials loaded = store.loadCredentials(credentialsPath);
        assertEquals(computerId, loaded.computerId());
        assertEquals(token, loaded.agentToken());
    }

    @Test
    void testResolveFromSystemProperties(@TempDir Path tempDir) throws IOException {
        SecureCredentialStore store = new SecureCredentialStore();
        Path credentialsPath = tempDir.resolve(".agent-credentials");

        UUID computerId = UUID.randomUUID();
        String token = "prop-token-987";

        System.setProperty("agent.computer-id", computerId.toString());
        System.setProperty("agent.token", token);
        try {
            AgentCredentials resolved = store.resolveCredentials(new String[0], credentialsPath);
            assertEquals(computerId, resolved.computerId());
            assertEquals(token, resolved.agentToken());
        } finally {
            System.clearProperty("agent.computer-id");
            System.clearProperty("agent.token");
        }
    }
}
