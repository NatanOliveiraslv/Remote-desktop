package com.br.remote_agent.websocket;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.awt.Toolkit;
import java.awt.Dimension;

import com.br.remote_agent.control.RemoteDesktopController;
import com.br.remote_agent.security.AgentCredentials;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** WebSocket client running on the computer that may be remotely controlled. */
public final class AgentWebSocketHandler implements WebSocket.Listener {

    private static final int MAX_SCREEN_WIDTH = 1280;
    private static final float JPEG_QUALITY = 0.55f;
    private static final long SCREEN_FRAME_INTERVAL_MS = 1000L; // Start at 1 FPS.
    private static final long RECONNECT_DELAY_SECONDS = 5L;

    private final URI serverUri;
    private final AgentCredentials credentials;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RemoteDesktopController desktopController;
    private final StringBuilder incomingText = new StringBuilder();

    private final ScheduledExecutorService screenCaptureExecutor = Executors
            .newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "remote-agent-screen-capture");
                thread.setDaemon(true);
                return thread;
            });

    private final ScheduledExecutorService reconnectExecutor = Executors
            .newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "remote-agent-reconnect");
                thread.setDaemon(true);
                return thread;
            });

    private volatile WebSocket webSocket;
    private volatile UUID activeRemoteSessionId;
    private volatile ScheduledFuture<?> screenCaptureTask;
    private volatile boolean isClosed = false;
    private volatile boolean isConnecting = false;

    public AgentWebSocketHandler(URI serverUri, AgentCredentials credentials) throws Exception {
        this.serverUri = Objects.requireNonNull(serverUri, "serverUri must not be null");
        this.credentials = Objects.requireNonNull(credentials, "credentials must not be null");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.desktopController = new RemoteDesktopController();
    }

    public AgentWebSocketHandler(URI serverUri, UUID computerId, String agentToken) throws Exception {
        this(serverUri, new AgentCredentials(computerId, agentToken));
    }

    public AgentCredentials getCredentials() {
        return credentials;
    }

    public synchronized void connect() {
        if (isClosed) {
            return;
        }

        if (webSocket != null && !webSocket.isInputClosed() && !webSocket.isOutputClosed()) {
            return;
        }

        isConnecting = true;
        URI handshakeUri = buildHandshakeUri();
        System.out.println("Connecting to " + serverUri + " as computer " + credentials.computerId() + " ...");

        httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .header("X-Agent-Token", credentials.agentToken())
                .header("X-Computer-Id", credentials.computerId().toString())
                .buildAsync(handshakeUri, this)
                .whenComplete((socket, error) -> {
                    isConnecting = false;
                    if (error != null) {
                        System.err.println("WebSocket connection failed: " + error.getMessage());
                        String msg = error.getMessage() != null ? error.getMessage() : "";
                        if (msg.contains("403") || msg.contains("handshake") || msg.contains("rejected")) {
                            System.err.println("Authentication rejected by server. Verify computerId and agentToken.");
                        }
                        scheduleReconnect();
                    } else {
                        this.webSocket = socket;
                    }
                });
    }

    private URI buildHandshakeUri() {
        String base = serverUri.toString();
        char separator = base.contains("?") ? '&' : '?';
        String encodedComputerId = URLEncoder.encode(credentials.computerId().toString(), StandardCharsets.UTF_8);
        String encodedToken = URLEncoder.encode(credentials.agentToken(), StandardCharsets.UTF_8);

        // Include both 'token' and 'agentToken' query parameters for broad server compatibility
        String query = String.format("computerId=%s&token=%s&agentToken=%s",
                encodedComputerId, encodedToken, encodedToken);
        return URI.create(base + separator + query);
    }

    private synchronized void scheduleReconnect() {
        if (isClosed || isConnecting) {
            return;
        }

        System.out.printf("Scheduling reconnection attempt in %d seconds...%n", RECONNECT_DELAY_SECONDS);
        reconnectExecutor.schedule(() -> {
            if (!isClosed && (webSocket == null || webSocket.isInputClosed() || webSocket.isOutputClosed())) {
                connect();
            }
        }, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public void onOpen(WebSocket socket) {
        this.webSocket = socket;
        System.out.println("Connected and authenticated with remote-server for computer: " + credentials.computerId());
        // Registration is performed at handshake time by AgentHandshakeInterceptor
        socket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
        String completeMessage = null;
        synchronized (incomingText) {
            incomingText.append(data);
            if (last) {
                completeMessage = incomingText.toString();
                incomingText.setLength(0);
            }
        }

        if (completeMessage != null) {
            handleMessage(completeMessage);
        }

        socket.request(1);
        return CompletableFuture.completedFuture(null);
    }

    private void handleMessage(String message) {
        System.out.println("Server -> Agent: " + message);
        try {
            JsonNode json = objectMapper.readTree(message);
            String type = json.path("type").asText();

            switch (type) {
                case "REMOTE_CONNECTION_REQUEST" -> handleRemoteConnectionRequest(json);
                case "MOUSE_MOVE" -> handleMouseMove(json);
                case "REMOTE_SESSION_CLOSED" -> handleRemoteSessionClosed(json);
                case "MOUSE_BUTTON_DOWN" -> handleMouseButtonDown(json);
                case "MOUSE_BUTTON_UP" -> handleMouseButtonUp(json);
                default -> System.out.println("No handler for message type: " + type);
            }
        } catch (Exception e) {
            System.err.println("Could not process server message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleRemoteConnectionRequest(JsonNode json) {
        String value = json.path("remoteSessionId").asText();
        if (value.isBlank()) {
            System.err.println("REMOTE_CONNECTION_REQUEST has no remoteSessionId; cannot accept it.");
            return;
        }

        UUID requestedSessionId;
        try {
            requestedSessionId = UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid remoteSessionId: " + value);
            return;
        }

        // MVP only: accepts automatically. Explicit permission should be added before
        // real use.
        stopScreenCapture();
        activeRemoteSessionId = requestedSessionId;
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

        sendJson(Map.of(
                "type", "CONNECTION_ACCEPTED",
                "remoteSessionId", requestedSessionId.toString(),
                "screenWidth", screenSize.width,
                "screenHeight", screenSize.height));
        System.out.println(screenSize.width + "x" + screenSize.height + " screen size sent to server.");
        startScreenCapture();
        System.out.println("Accepted remote session request: " + requestedSessionId);
    }

    private void handleMouseMove(JsonNode json) {
        if (!isMessageForActiveSession(json)) {
            System.out.println("Ignoring MOUSE_MOVE for a missing or different session.");
            return;
        }

        JsonNode xNode = json.path("x");
        JsonNode yNode = json.path("y");
        if (!xNode.isIntegralNumber() || !yNode.isIntegralNumber()) {
            System.err.println("MOUSE_MOVE must contain integer x and y values.");
            return;
        }

        desktopController.moveMouse(xNode.intValue(), yNode.intValue());
    }

    private void handleMouseButtonDown(JsonNode json) {
        if (!isActiveSession(json)) {
            return;
        }

        String button = json.path("button").asText();

        JsonNode xNode = json.path("x");
        JsonNode yNode = json.path("y");

        if (!xNode.isIntegralNumber()
                || !yNode.isIntegralNumber()) {
            return;
        }

        int x = xNode.intValue();
        int y = yNode.intValue();

        desktopController.moveMouse(x, y);
        desktopController.pressMouse(button);
    }

    private void handleMouseButtonUp(JsonNode json) {
        if (!isActiveSession(json)) {
            return;
        }

        String button = json.path("button").asText();

        JsonNode xNode = json.path("x");
        JsonNode yNode = json.path("y");

        if (xNode.isIntegralNumber()
                && yNode.isIntegralNumber()) {

            desktopController.moveMouse(
                    xNode.intValue(),
                    yNode.intValue());
        }

        desktopController.releaseMouse(button);
    }

    private void handleRemoteSessionClosed(JsonNode json) {
        String id = json.path("remoteSessionId").asText();
        if (activeRemoteSessionId != null && activeRemoteSessionId.toString().equals(id)) {
            activeRemoteSessionId = null;
            stopScreenCapture();
            System.out.println("Remote session ended: " + id);
        }
    }

    private boolean isActiveSession(JsonNode json) {
        String id = json.path("remoteSessionId").asText();

        return activeRemoteSessionId != null
                && activeRemoteSessionId.toString().equals(id);
    }

    private boolean isMessageForActiveSession(JsonNode json) {
        if (activeRemoteSessionId == null) {
            return false;
        }
        String id = json.path("remoteSessionId").asText();
        return activeRemoteSessionId.toString().equals(id);
    }

    private synchronized void startScreenCapture() {
        stopScreenCapture();
        screenCaptureTask = screenCaptureExecutor.scheduleWithFixedDelay(
                this::captureAndSendScreen,
                SCREEN_FRAME_INTERVAL_MS,
                SCREEN_FRAME_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
        System.out.println("Screen capture started at approximately 1 FPS.");
    }

    private synchronized void stopScreenCapture() {
        ScheduledFuture<?> currentTask = screenCaptureTask;
        screenCaptureTask = null;
        if (currentTask != null) {
            currentTask.cancel(false);
        }
    }

    private void captureAndSendScreen() {
        UUID sessionId = activeRemoteSessionId;
        WebSocket socket = webSocket;
        if (sessionId == null || socket == null) {
            return;
        }

        try {
            byte[] jpeg = desktopController.captureScreenJpeg(
                    MAX_SCREEN_WIDTH,
                    JPEG_QUALITY);

            // Screen frames are binary JPEG WebSocket messages, not Base64 JSON.
            socket.sendBinary(ByteBuffer.wrap(jpeg), true).join();
            System.out.printf(
                    "Screen frame sent: %d bytes, session=%s%n",
                    jpeg.length,
                    sessionId);
        } catch (Exception e) {
            System.err.println("Could not capture/send screen frame: " + e.getMessage());
        }
    }

    private void sendJson(Map<String, ?> payload) {
        WebSocket socket = this.webSocket;
        if (socket == null) {
            System.err.println("Cannot send message: WebSocket is not connected.");
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(payload);
            socket.sendText(json, true).whenComplete((ignored, error) -> {
                if (error != null) {
                    System.err.println("Error sending WebSocket message: " + error.getMessage());
                } else {
                    System.out.println("Agent -> Server: " + json);
                }
            });
        } catch (Exception e) {
            System.err.println("Could not serialize WebSocket message: " + e.getMessage());
        }
    }

    public void close() {
        isClosed = true;
        stopScreenCapture();
        screenCaptureExecutor.shutdownNow();
        reconnectExecutor.shutdownNow();
        WebSocket socket = this.webSocket;
        if (socket != null) {
            try {
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "Agent shutting down").join();
            } catch (Exception e) {
                System.err.println("Error closing WebSocket: " + e.getMessage());
                socket.abort();
            }
        }
    }

    @Override
    public CompletionStage<?> onClose(WebSocket socket, int statusCode, String reason) {
        activeRemoteSessionId = null;
        stopScreenCapture();
        System.out.println("Disconnected from server. Code=" + statusCode + ", reason=" + reason);
        scheduleReconnect();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void onError(WebSocket socket, Throwable error) {
        System.err.println("WebSocket error: " + error.getMessage());
        scheduleReconnect();
    }
}
