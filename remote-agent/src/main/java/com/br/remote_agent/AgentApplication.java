package com.br.remote_agent;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

import com.br.remote_agent.security.AgentCredentials;
import com.br.remote_agent.security.SecureCredentialStore;
import com.br.remote_agent.websocket.AgentWebSocketHandler;

/**
 * Entry point for the standalone remote Agent.
 * Authenticates with remote-server via secure credentials (computerId and agentToken).
 */
public final class AgentApplication {

    private static final String DEFAULT_SERVER_URI = "ws://localhost:8080/api/v1/ws/agent";
    private static final Path DEFAULT_CREDENTIALS_FILE = Path.of(".agent-credentials");
    private static final Path LEGACY_COMPUTER_ID_FILE = Path.of("computer-id.txt");

    private AgentApplication() {
    }

    public static void main(String[] args) throws InterruptedException {
        String configuredUri = resolveConfig(
                args,
                "--server-uri",
                "agent.server-uri",
                "AGENT_SERVER_URI",
                DEFAULT_SERVER_URI
        );

        String credentialsFilePath = resolveConfig(
                args,
                "--credentials-file",
                "agent.credentials.file",
                "AGENT_CREDENTIALS_FILE",
                DEFAULT_CREDENTIALS_FILE.toString()
        );
        Path credentialsPath = Path.of(credentialsFilePath);

        checkLegacyCredentials(credentialsPath);

        try {
            SecureCredentialStore credentialStore = new SecureCredentialStore();
            AgentCredentials credentials = credentialStore.resolveCredentials(args, credentialsPath);

            AgentWebSocketHandler handler = new AgentWebSocketHandler(
                    URI.create(configuredUri), credentials
            );

            Runtime.getRuntime().addShutdownHook(new Thread(handler::close));

            System.out.println("==================================================");
            System.out.println(" Remote Agent Starting");
            System.out.println(" Server:           " + configuredUri);
            System.out.println(" Computer ID:      " + credentials.computerId());
            System.out.println(" Agent Token:      " + credentials.maskedToken());
            System.out.println(" Credentials File: " + credentialsPath.toAbsolutePath());
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

    private static String resolveConfig(String[] args, String flagName, String sysProp, String envVar, String defaultValue) {
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                if (flagName.equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                    return args[i + 1].trim();
                }
            }
        }

        String propVal = System.getProperty(sysProp);
        if (propVal != null && !propVal.isBlank()) {
            return propVal.trim();
        }

        String envVal = System.getenv(envVar);
        if (envVal != null && !envVal.isBlank()) {
            return envVal.trim();
        }

        return defaultValue;
    }
}
