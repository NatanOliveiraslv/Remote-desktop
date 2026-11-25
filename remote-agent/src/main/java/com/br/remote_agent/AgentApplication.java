package com.br.remote_agent;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

import com.br.remote_agent.config.AgentConfig;
import com.br.remote_agent.security.AgentCredentials;
import com.br.remote_agent.security.SecureCredentialStore;
import com.br.remote_agent.websocket.AgentWebSocketHandler;

/**
 * Entry point for the standalone remote Agent.
 * Authenticates with remote-server via secure credentials (computerId and agentToken).
 */
public final class AgentApplication {

    private static final Path LEGACY_COMPUTER_ID_FILE = Path.of("computer-id.txt");

    private AgentApplication() {
    }

    public static void main(String[] args) throws InterruptedException {
        AgentConfig config = AgentConfig.load(args);
        Path credentialsPath = config.credentialsFile();

        checkLegacyCredentials(credentialsPath);

        try {
            SecureCredentialStore credentialStore = new SecureCredentialStore();
            AgentCredentials credentials = credentialStore.resolveCredentials(args, credentialsPath);

            AgentWebSocketHandler handler = new AgentWebSocketHandler(
                    URI.create(config.serverUri()), credentials, config
            );

            Runtime.getRuntime().addShutdownHook(new Thread(handler::close));

            System.out.println("==================================================");
            System.out.println(" Remote Agent Starting");
            System.out.println(" Server:           " + config.serverUri());
            System.out.println(" Computer ID:      " + credentials.computerId());
            System.out.println(" Agent Token:      " + credentials.maskedToken());
            System.out.println(" Credentials File: " + credentialsPath.toAbsolutePath());
            System.out.printf(" Stream Config:    maxWidth=%dpx, quality=%.2f, fps=%d%n",
                    config.maxWidth(), config.jpegQuality(), config.targetFps());
            System.out.println(" Keep this terminal open while using the Agent.");
            System.out.println("==================================================");

            handler.connect();
            new CountDownLatch(1).await();
        } catch (IllegalArgumentException e) {
            System.err.println("Configuration error: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("I/O error with credentials: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Could not start Agent: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void checkLegacyCredentials(Path targetCredentialsPath) {
        if (!Files.exists(targetCredentialsPath) && Files.exists(LEGACY_COMPUTER_ID_FILE)) {
            System.out.println("[Notice] Found legacy computer-id.txt.");
            System.out.println("[Notice] remote-server now requires authentication via both computerId and agentToken.");
            System.out.println("[Notice] Please register the computer in remote-server (POST /computers/create) to obtain its token.");
        }
    }
}
