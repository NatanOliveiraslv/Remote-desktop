package com.br.remote_agent.websocket;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.br.remote_agent.control.RemoteDesktopController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

public class AgentWebSocketHandler implements WebSocket.Listener {

    private final URI serverUri;
    private final UUID computerId;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final RemoteDesktopController desktopController;

    private final StringBuilder messageBuffer = new StringBuilder();

    private volatile WebSocket webSocket;
    private volatile UUID activeRemoteSessionId;

    public AgentWebSocketHandler(URI serverUri, UUID computerId) throws Exception {
        this.serverUri = serverUri;
        this.computerId = computerId;

        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.desktopController = new RemoteDesktopController();
    }

    public void connect() {
        httpClient.newWebSocketBuilder()
                .buildAsync(serverUri, this)
                .whenComplete((socket, error) -> {
                    if (error != null) {
                        System.err.println(
                                "Failed to connect to server: "
                                        + error.getMessage());
                    } else {
                        this.webSocket = socket;
                    }
                })
                .join();
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        this.webSocket = webSocket;

        System.out.println("Connected to Spring Boot server!");

        // Registra o computador no servidor.
        sendJson(webSocket, Map.of(
                "type", "AGENT_REGISTER",
                "computerId", computerId.toString()));

        webSocket.request(1);
    }

    @Override
    public CompletableFuture<?> onText(
            WebSocket webSocket,
            CharSequence data,
            boolean last) {

        messageBuffer.append(data);

        if (last) {
            String message = messageBuffer.toString();
            messageBuffer.setLength(0);

            handleMessage(webSocket, message);
        }

        // Solicita a próxima mensagem WebSocket.
        webSocket.request(1);

        return CompletableFuture.completedFuture(null);
    }

    private void handleMessage( WebSocket webSocket, String message){

        try {
            System.out.println("Message received: " + message);

            JsonNode json = objectMapper.readTree(message);
            String type = json.path("type").asText();

            switch (type) {

                case "REMOTE_CONNECTION_REQUEST" -> {
                    String id = json.path("remoteSessionId").asText();

                    if (id.isBlank()) {
                        System.err.println(
                                "Missing remoteSessionId");
                        return;
                    }

                    UUID requestedSessionId = UUID.fromString(id);

                    // Guarda a sessão remota solicitada pelo servidor.
                    activeRemoteSessionId = requestedSessionId;

                    // Confirma que o Agent recebeu a solicitação.
                    sendJson(webSocket, Map.of(
                            "type", "CONNECTION_ACCEPTED",
                            "remoteSessionId",
                            requestedSessionId.toString()));
                }

                case "MOUSE_MOVE" -> {
                    String id = json.path("remoteSessionId").asText();

                    // Só aceita comandos da sessão atualmente ativa.
                    if (activeRemoteSessionId == null
                            || !activeRemoteSessionId.toString()
                                    .equals(id)) {

                        System.out.println(
                                "Mouse command rejected: invalid session");
                        return;
                    }

                    JsonNode xNode = json.path("x");
                    JsonNode yNode = json.path("y");

                    if (!xNode.isIntegralNumber()
                            || !yNode.isIntegralNumber()) {

                        System.err.println(
                                "Invalid mouse coordinates");
                        return;
                    }

                    int x = xNode.intValue();
                    int y = yNode.intValue();

                    desktopController.moveMouse(x, y);
                }

                default -> System.out.println(
                        "Unknown message type: " + type);
            }

        } catch (Exception e) {
            System.err.println(
                    "Error processing message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void sendJson(
            WebSocket webSocket,
            Map<String, ?> data) {

        try {
            String json = objectMapper.writeValueAsString(data);

            webSocket.sendText(json, true);

        } catch (Exception e) {
            System.err.println(
                    "Error sending message: " + e.getMessage());
        }
    }

    @Override
    public CompletableFuture<?> onClose(
            WebSocket webSocket,
            int statusCode,
            String reason) {

        activeRemoteSessionId = null;

        System.out.println(
                "Disconnected from server. Status: "
                        + statusCode + ", reason: " + reason);

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void onError(
            WebSocket webSocket,
            Throwable error) {

        System.err.println(
                "WebSocket error: " + error.getMessage());
    }
}