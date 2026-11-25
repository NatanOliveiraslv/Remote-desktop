package com.br.remote_agent.websocket;

import java.awt.Rectangle;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.br.remote_agent.capture.DirtyRectEngine;
import com.br.remote_agent.config.AgentConfig;
import com.br.remote_agent.config.QualityPreset;
import com.br.remote_agent.control.RemoteDesktopController;
import com.br.remote_agent.security.AgentCredentials;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** WebSocket client running on the computer that may be remotely controlled. */
public final class AgentWebSocketHandler implements WebSocket.Listener {

    private static final long RECONNECT_DELAY_SECONDS = 5L;
    private static final long MAX_INTERVAL_MS = 1000L;
    private static final long METRICS_LOG_INTERVAL_MS = 1000L;

    private final URI serverUri;
    private final AgentCredentials credentials;
    private final int targetFps;
    private final long targetIntervalMs;
    private final int configuredMaxWidth;
    private final float configuredQuality;
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

    // Estado da transmissão de tela (ver startScreenCapture/captureAndSendScreen).
    private volatile long currentIntervalMs;
    private volatile double smoothedFrameDurationMs;
    private volatile long metricsWindowStartMs = System.currentTimeMillis();
    private final AtomicInteger framesSentInWindow = new AtomicInteger();
    private final AtomicLong bytesSentInWindow = new AtomicLong();

    // Qualidade e resolução dinâmicas (preset escolhido no client + zoom + adaptação de rede).
    private volatile QualityPreset activePreset = QualityPreset.MEDIUM;
    private volatile int baseMaxWidth;
    private volatile float minQuality;
    private volatile float maxQuality;
    private volatile float adaptiveQuality;
    private volatile double activeZoomScale = 1.0;
    private volatile int idleFrameCount = 0;
    private volatile float lastSentQuality = 0.55f;

    // Dirty-rectangle capture engine: compara blocos em vez de recodificar a tela toda.
    private volatile DirtyRectEngine dirtyRectEngine;

    public AgentWebSocketHandler(URI serverUri, AgentCredentials credentials) throws Exception {
        this(serverUri, credentials, new AgentConfig(
                serverUri.toString(),
                java.nio.file.Path.of(AgentConfig.DEFAULT_CREDENTIALS_FILE),
                AgentConfig.DEFAULT_MAX_WIDTH,
                AgentConfig.DEFAULT_QUALITY,
                AgentConfig.DEFAULT_FPS));
    }

    public AgentWebSocketHandler(URI serverUri, AgentCredentials credentials, AgentConfig config) throws Exception {
        this.serverUri = Objects.requireNonNull(serverUri, "serverUri must not be null");
        this.credentials = Objects.requireNonNull(credentials, "credentials must not be null");
        Objects.requireNonNull(config, "config must not be null");
        this.targetFps = config.targetFps();
        this.targetIntervalMs = 1000L / this.targetFps;
        this.currentIntervalMs = this.targetIntervalMs;
        this.smoothedFrameDurationMs = this.targetIntervalMs;
        this.configuredMaxWidth = config.maxWidth();
        this.configuredQuality = config.jpegQuality();
        this.baseMaxWidth = config.maxWidth();
        this.adaptiveQuality = config.jpegQuality();
        this.minQuality = Math.max(0.25f, config.jpegQuality() - 0.20f);
        this.maxQuality = Math.min(0.92f, config.jpegQuality() + 0.20f);
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
                case "SET_QUALITY" -> handleSetQuality(json);
                case "SET_ZOOM" -> handleSetZoom(json);
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
        Rectangle screenBounds = desktopController.getPrimaryScreenBounds();
        double hidpiScale = desktopController.getPrimaryScreenScale();

        sendJson(Map.of(
                "type", "CONNECTION_ACCEPTED",
                "remoteSessionId", requestedSessionId.toString(),
                "screenWidth", screenBounds.width,
                "screenHeight", screenBounds.height));
        System.out.printf("%dx%d logical screen size sent to server (HiDPI scale=%.2f).%n",
                screenBounds.width, screenBounds.height, hidpiScale);
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

    private void handleSetQuality(JsonNode json) {
        if (!isMessageForActiveSession(json)) {
            return;
        }
        QualityPreset preset = QualityPreset.fromString(json.path("preset").asText());
        this.activePreset = preset;
        this.baseMaxWidth = preset.maxWidth();
        this.minQuality = preset.minQuality();
        this.maxQuality = preset.maxQuality();
        this.adaptiveQuality = preset.baseQuality();
        applyResolutionAndQuality(true);
        System.out.printf("Quality preset changed to %s (maxWidth=%d, quality=%.2f)%n",
                preset, baseMaxWidth, adaptiveQuality);
    }

    private void handleSetZoom(JsonNode json) {
        if (!isMessageForActiveSession(json)) {
            return;
        }
        double zoomScale = json.path("zoomScale").asDouble(1.0);
        double clamped = Math.max(1.0, Math.min(4.0, zoomScale));
        if (Math.abs(clamped - this.activeZoomScale) < 0.05) {
            return;
        }
        this.activeZoomScale = clamped;
        applyResolutionAndQuality(clamped > 1.25);
        System.out.printf("Client zoom changed to %.2fx -> effectiveMaxWidth=%d%n",
                clamped, computeEffectiveMaxWidth());
    }

    private int computeEffectiveMaxWidth() {
        if (activeZoomScale <= 1.25) {
            return baseMaxWidth;
        }
        // Quando o usuario da zoom, aumenta a resolucao ate o nativo (cap 3840px) para manter o texto nitido.
        return Math.min(3840, (int) Math.round(baseMaxWidth * Math.min(activeZoomScale, 2.5)));
    }

    private float computeEffectiveQuality() {
        float zoomBoost = activeZoomScale > 1.25 ? 0.10f : 0.0f;
        return Math.min(0.92f, adaptiveQuality + zoomBoost);
    }

    private void applyResolutionAndQuality(boolean forceRefresh) {
        DirtyRectEngine engine = this.dirtyRectEngine;
        if (engine == null) {
            return;
        }
        engine.setMaxWidth(computeEffectiveMaxWidth());
        engine.setJpegQuality(computeEffectiveQuality());
        if (forceRefresh) {
            engine.forceKeyframe();
        }
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
        currentIntervalMs = targetIntervalMs;
        smoothedFrameDurationMs = targetIntervalMs;
        activeZoomScale = 1.0;
        baseMaxWidth = configuredMaxWidth;
        adaptiveQuality = configuredQuality;
        minQuality = Math.max(0.25f, configuredQuality - 0.20f);
        maxQuality = Math.min(0.92f, configuredQuality + 0.20f);
        idleFrameCount = 0;
        lastSentQuality = configuredQuality;

        dirtyRectEngine = new DirtyRectEngine(
                desktopController.getRobot(), baseMaxWidth, adaptiveQuality);
        resetMetricsWindow();
        scheduleCapture(0L);
        System.out.printf("Screen capture started (dirty rectangles, tile %dpx, maxWidth=%dpx, quality=%.2f), target %d FPS.%n",
                DirtyRectEngine.DEFAULT_TILE_SIZE, baseMaxWidth, adaptiveQuality, targetFps);
    }

    private synchronized void stopScreenCapture() {
        ScheduledFuture<?> currentTask = screenCaptureTask;
        screenCaptureTask = null;
        if (currentTask != null) {
            currentTask.cancel(false);
        }
        DirtyRectEngine engine = dirtyRectEngine;
        dirtyRectEngine = null;
        if (engine != null) {
            engine.close();
        }
    }

    // Agenda a próxima captura. Nunca há duas capturas/envios em andamento ao
    // mesmo tempo: a próxima só é agendada quando a anterior já terminou
    // (veja captureAndSendScreen), o que evita acumular uma fila de quadros
    // quando a rede ou o envio ficam mais lentos que o intervalo-alvo.
    private synchronized void scheduleCapture(long delayMs) {
        if (isClosed || activeRemoteSessionId == null) {
            return;
        }
        screenCaptureTask = screenCaptureExecutor.schedule(
                this::captureAndSendScreen, delayMs, TimeUnit.MILLISECONDS);
    }

    private void captureAndSendScreen() {
        UUID sessionId = activeRemoteSessionId;
        WebSocket socket = webSocket;
        DirtyRectEngine engine = dirtyRectEngine;
        if (sessionId == null || socket == null || engine == null) {
            return;
        }

        long captureStartMs = System.currentTimeMillis();
        DirtyRectEngine.CaptureResult result;

        try {
            result = engine.captureDirtyTiles();
        } catch (Exception e) {
            System.err.println("Could not capture screen frame: " + e.getMessage());
            scheduleCapture(currentIntervalMs);
            return;
        }

        // null = zero tiles changed and not a keyframe → skip sending.
        // Aproveita que a tela esta parada para subir a qualidade gradualmente e
        // refinar o texto se o ultimo frame foi enviado com qualidade reduzida.
        if (result == null) {
            idleFrameCount++;
            if (idleFrameCount % 3 == 0 && adaptiveQuality < maxQuality) {
                adaptiveQuality = Math.min(maxQuality, adaptiveQuality + 0.05f);
                engine.setJpegQuality(computeEffectiveQuality());
                if (computeEffectiveQuality() - lastSentQuality >= 0.10f) {
                    engine.forceKeyframe();
                }
            }
            long elapsed = System.currentTimeMillis() - captureStartMs;
            scheduleCapture(Math.max(0L, currentIntervalMs - elapsed));
            return;
        }

        idleFrameCount = 0;
        lastSentQuality = computeEffectiveQuality();

        ByteBuffer payload = result.payload();
        int totalBytes = result.totalJpegBytes();

        long sendStartMs = System.currentTimeMillis();

        socket.sendBinary(payload, true).whenComplete((ignored, error) -> {
            long sendDoneMs = System.currentTimeMillis();
            long sendDurationMs = sendDoneMs - sendStartMs;
            long frameDurationMs = sendDoneMs - captureStartMs;

            if (result.isKeyframe()) {
                System.out.printf(
                        "KEYFRAME sent: %d/%d tiles, %d bytes, q=%.2f w=%d, cap=%dms scale=%dms diff=%dms enc=%dms send=%dms total=%dms%n",
                        result.dirtyTileCount(), result.totalTileCount(), totalBytes,
                        lastSentQuality, engine.getMaxWidth(),
                        result.captureMs(), result.scaleMs(), result.diffMs(), result.encodeMs(),
                        sendDurationMs, frameDurationMs);
            } else {
                System.out.printf(
                        "Dirty tiles sent: %d/%d, %d bytes, q=%.2f w=%d, cap=%dms scale=%dms diff=%dms enc=%dms send=%dms total=%dms%n",
                        result.dirtyTileCount(), result.totalTileCount(), totalBytes,
                        lastSentQuality, engine.getMaxWidth(),
                        result.captureMs(), result.scaleMs(), result.diffMs(), result.encodeMs(),
                        sendDurationMs, frameDurationMs);
            }

            if (error != null) {
                System.err.println("Error sending screen frame: " + error.getMessage());
            } else {
                recordFrameSent(totalBytes);
            }

            adaptQualityForNetwork(frameDurationMs, sendDurationMs, engine);
            adaptInterval(frameDurationMs);
            // Subtrai o tempo já gasto neste quadro para atingir o FPS-alvo
            // sem somar (frameDuration + currentInterval).
            long nextDelayMs = Math.max(0L, currentIntervalMs - frameDurationMs);
            scheduleCapture(nextDelayMs);
        });
    }

    private void adaptQualityForNetwork(long frameDurationMs, long sendDurationMs, DirtyRectEngine engine) {
        if (sendDurationMs > 30 || frameDurationMs > targetIntervalMs * 1.6) {
            if (adaptiveQuality > minQuality) {
                adaptiveQuality = Math.max(minQuality, adaptiveQuality - 0.05f);
                engine.setJpegQuality(computeEffectiveQuality());
            }
        } else if (frameDurationMs < targetIntervalMs * 0.8 && sendDurationMs <= 5) {
            if (adaptiveQuality < maxQuality) {
                adaptiveQuality = Math.min(maxQuality, adaptiveQuality + 0.02f);
                engine.setJpegQuality(computeEffectiveQuality());
            }
        }
    }

    // Intervalo adaptativo: se captura + envio demoraram mais que o intervalo
    // atual, a rede ou a CPU não acompanham a meta, então desacelera para não
    // acumular atraso. Se sobrou folga, volta aos poucos em direção à meta.
    // Média móvel exponencial (EWMA): cada quadro novo pesa ALPHA sobre a
    // média anterior. Isto substitui a versão antiga, que ao ver um quadro
    // lento isolado (ex.: uma pausa do coletor de lixo) travava o intervalo
    // naquele valor alto e só se recuperava 10ms por quadro — com o
    // intervalo já alto, os quadros ficam raros, e essa recuperação podia
    // levar segundos a mais em tempo real. A média suaviza um pico isolado
    // em poucos quadros, mas ainda desacelera de verdade quando a lentidão
    // é sustentada (vários quadros lentos seguidos), que é o que importa.
    private static final double ALPHA = 0.3;

    private void adaptInterval(long frameDurationMs) {
        smoothedFrameDurationMs =
                ALPHA * frameDurationMs + (1 - ALPHA) * smoothedFrameDurationMs;
        currentIntervalMs = (long) Math.max(
                targetIntervalMs,
                Math.min(MAX_INTERVAL_MS, smoothedFrameDurationMs));
    }

    private void recordFrameSent(int jpegBytes) {
        int frames = framesSentInWindow.incrementAndGet();
        long bytes = bytesSentInWindow.addAndGet(jpegBytes);

        long now = System.currentTimeMillis();
        long windowStart = metricsWindowStartMs;
        long elapsedMs = now - windowStart;

        if (elapsedMs >= METRICS_LOG_INTERVAL_MS) {
            resetMetricsWindow();
            double seconds = elapsedMs / 1000.0;
            System.out.printf(
                    "Screen metrics: %.1f FPS, %.1f KB/s, interval=%dms%n",
                    frames / seconds,
                    (bytes / 1024.0) / seconds,
                    currentIntervalMs);
        }
    }

    private void resetMetricsWindow() {
        metricsWindowStartMs = System.currentTimeMillis();
        framesSentInWindow.set(0);
        bytesSentInWindow.set(0);
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
