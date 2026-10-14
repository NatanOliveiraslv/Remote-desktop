package com.br.remote_agent;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import com.br.remote_agent.websocket.AgentWebSocketHandler;

/** Entry point for the standalone remote Agent. */
public final class AgentApplication {

    private static final String DEFAULT_SERVER_URI =
            "ws://localhost:8080/api/v1/ws/agent";
    private static final Path COMPUTER_ID_FILE = Path.of("computer-id.txt");

    private AgentApplication() {
    }

    public static void main(String[] args) throws InterruptedException {
        String configuredUri = System.getProperty(
                "agent.server-uri",
                args.length > 0 ? args[0] : DEFAULT_SERVER_URI
        );

        try {
            UUID computerId = loadOrCreateComputerId(COMPUTER_ID_FILE);
            AgentWebSocketHandler handler = new AgentWebSocketHandler(
                    URI.create(configuredUri), computerId
            );

            Runtime.getRuntime().addShutdownHook(new Thread(handler::close));

            System.out.println("Remote Agent starting...");
            System.out.println("Server: " + configuredUri);
            System.out.println("Computer ID: " + computerId);
            System.out.println("Keep this terminal open while using the Agent.");

            handler.connect();
            new CountDownLatch(1).await();
        } catch (IOException e) {
            System.err.println("Could not read or save computer-id.txt: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid server WebSocket URI: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Could not start Agent: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static UUID loadOrCreateComputerId(Path file) throws IOException {
        if (Files.exists(file)) {
            String value = Files.readString(file, StandardCharsets.UTF_8).trim();
            try {
                return UUID.fromString(value);
            } catch (IllegalArgumentException e) {
                throw new IOException(
                        "computer-id.txt does not contain a valid UUID. "
                                + "Correct or delete the file to generate a new ID.", e
                );
            }
        }

        UUID id = UUID.randomUUID();
        Files.writeString(file, id.toString() + System.lineSeparator(), StandardCharsets.UTF_8);
        return id;
    }
}
