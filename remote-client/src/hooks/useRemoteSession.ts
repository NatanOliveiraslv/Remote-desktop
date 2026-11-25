import { useEffect, useRef, useState } from "react";
import type { RefObject } from "react";
import { API_BASE, expireSession, getValidAccessToken } from "../api/http";
import { paintTilesToCanvas, parseTileFrame } from "../utils/tileFrame";

// Janela usada para calcular o FPS exibido ao vivo.
const FPS_WINDOW_MS = 1000;

export type ConnectionStatus =
  | "disconnected"
  | "connecting"
  | "connected"
  | "error";

type ServerMessage = {
  type?: string;
  message?: string;
  remoteSessionId?: string;
  computerId?: string;
  screenWidth?: number;
  screenHeight?: number;
};

// Mensagens de erro do servidor, traduzidas para exibir ao usuário.
const SERVER_ERRORS: Record<string, string> = {
  "Computer offline": "O computador está offline.",
  "Computer already has an active session": "Este computador já está em uso em outra sessão.",
  "Agent disconnected": "O Agent do computador foi desconectado.",
  "Invalid or inactive remote session": "A sessão remota não está mais ativa.",
  "Failed to initiate remote session": "Não foi possível iniciar a sessão remota.",
  "Access denied": "Você não tem permissão para acessar este computador.",
};

function translateServerError(message?: string): string {
  if (!message) return "Não foi possível conectar ao computador.";
  return SERVER_ERRORS[message] ?? message;
}

export interface RemoteSessionState {
  status: ConnectionStatus;
  statusMessage: string;
  remoteSessionId: string | null;
  hasFrame: boolean;
  canvasRef: RefObject<HTMLCanvasElement | null>;
  remoteScreenWidth: number | null;
  remoteScreenHeight: number | null;
  // Quadros recebidos no último segundo e latência do último quadro
  // (tempo entre a captura no Agent e a chegada aqui). `null` enquanto
  // nenhum quadro com cabeçalho de timestamp chegou ainda.
  fps: number;
  latencyMs: number | null;
  socketRef: RefObject<WebSocket | null>;
  connect: (computerId: string) => Promise<void>;
  disconnect: () => void;
}

export function useRemoteSession(): RemoteSessionState {
  const [status, setStatus] = useState<ConnectionStatus>("disconnected");
  const [statusMessage, setStatusMessage] = useState("");
  const [remoteSessionId, setRemoteSessionId] = useState<string | null>(null);
  const [hasFrame, setHasFrame] = useState(false);
  const [remoteScreenWidth, setRemoteScreenWidth] = useState<number | null>(
    null
  );
  const [remoteScreenHeight, setRemoteScreenHeight] = useState<number | null>(
    null
  );
  const [fps, setFps] = useState(0);
  const [latencyMs, setLatencyMs] = useState<number | null>(null);

  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  // Serializa a pintura de frames para que um frame mais rápido não
  // sobrescreva outro que ainda está decodificando tiles.
  const paintQueueRef = useRef<Promise<void>>(Promise.resolve());
  // Instante (Date.now) de cada quadro recebido no último segundo.
  const frameTimestampsRef = useRef<number[]>([]);
  const socketRef = useRef<WebSocket | null>(null);
  // Cada conexão ganha um número; se outra começar (ou o usuário cancelar)
  // enquanto esta espera o token, ela é descartada.
  const attemptRef = useRef(0);

  // Fecha o socket atual. Zerar a ref antes faz os handlers antigos
  // (onclose/onmessage) ignorarem eventos de um socket descartado.
  function closeSocket(reason: string) {
    const socket = socketRef.current;
    socketRef.current = null;

    if (socket && socket.readyState < WebSocket.CLOSING) {
      socket.close(1000, reason);
    }
  }

  // Limpa o canvas e os dados da sessão anterior.
  function resetSession() {
    const canvas = canvasRef.current;
    if (canvas) {
      const ctx = canvas.getContext("2d");
      ctx?.clearRect(0, 0, canvas.width, canvas.height);
    }

    paintQueueRef.current = Promise.resolve();
    setHasFrame(false);
    setRemoteSessionId(null);
    setRemoteScreenWidth(null);
    setRemoteScreenHeight(null);
    setFps(0);
    setLatencyMs(null);
    frameTimestampsRef.current = [];
  }

  async function connect(computerId: string) {
    const id = computerId.trim();

    if (!id) {
      setStatus("error");
      setStatusMessage("Computador inválido.");
      return;
    }

    const attempt = ++attemptRef.current;

    // Evita manter uma conexão anterior aberta.
    closeSocket("Starting a new connection");
    resetSession();

    setStatus("connecting");
    setStatusMessage("Conectando ao servidor...");

    // O navegador não envia headers no WebSocket, então o token vai na URL.
    // Ele só é conferido no momento da conexão; por isso pedimos um token
    // sempre válido antes de abrir.
    let token: string | null;

    try {
      token = await getValidAccessToken();
    } catch {
      if (attempt !== attemptRef.current) return;

      setStatus("error");
      setStatusMessage("Não foi possível acessar o servidor.");
      return;
    }

    if (attempt !== attemptRef.current) return;

    if (!token) {
      expireSession();
      return;
    }

    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const url = new URL(`${protocol}//${window.location.host}${API_BASE}/ws/client`);
    url.searchParams.set("access_token", token);

    const socket = new WebSocket(url);

    socket.binaryType = "arraybuffer";
    socketRef.current = socket;

    socket.onopen = () => {
      if (socketRef.current !== socket) return;

      setStatusMessage("Servidor conectado. Solicitando acesso...");

      socket.send(JSON.stringify({ type: "CONNECT", computerId: id }));
    };

    socket.onmessage = (event: MessageEvent) => {
      if (socketRef.current !== socket) return;

      // Mensagem binária: quadro de dirty tiles enviado pelo Agent.
      if (typeof event.data !== "string") {
        const buffer =
          event.data instanceof ArrayBuffer ? event.data : null;
        if (!buffer) return;

        const frame = parseTileFrame(buffer);
        if (!frame) return;

        paintQueueRef.current = paintQueueRef.current
          .then(async () => {
            if (socketRef.current !== socket) return;
            const canvas = canvasRef.current;
            if (!canvas) return;
            const ctx = canvas.getContext("2d");
            if (!ctx) return;

            await paintTilesToCanvas(canvas, ctx, frame);
            if (socketRef.current === socket) {
              setHasFrame(true);
            }
          })
          .catch((err) => {
            console.error("Erro ao pintar tiles no canvas:", err);
          });

        // Math.max(0, ...) porque o relógio do Agent e o do navegador não são
        // o mesmo: uma pequena diferença de ajuste entre eles pode dar um
        // número negativo, que não existe como latência.
        const now = Date.now();
        setLatencyMs(Math.max(0, now - frame.captureTimestamp));

        const timestamps = frameTimestampsRef.current;
        timestamps.push(now);
        const cutoff = now - FPS_WINDOW_MS;
        while (timestamps.length > 0 && timestamps[0] < cutoff) {
          timestamps.shift();
        }
        setFps(timestamps.length);

        return;
      }

      // A partir daqui continua o tratamento de mensagens JSON.
      let data: ServerMessage;

      try {
        data = JSON.parse(event.data) as ServerMessage;
      } catch {
        console.error("Mensagem inválida recebida:", event.data);
        return;
      }

      switch (data.type) {
        case "CONNECTING":
          setStatus("connecting");
          setStatusMessage(
            "Solicitação enviada. Aguardando resposta do Agent..."
          );

          if (data.remoteSessionId) {
            setRemoteSessionId(data.remoteSessionId);
          }
          break;

        case "CONNECTION_ACCEPTED":
          setStatus("connected");
          setStatusMessage("Conectado ao computador remoto.");

          if (data.remoteSessionId) {
            setRemoteSessionId(data.remoteSessionId);
          }
          if (
            data.screenWidth &&
            data.screenHeight &&
            data.screenWidth > 0 &&
            data.screenHeight > 0
          ) {
            setRemoteScreenWidth(data.screenWidth);
            setRemoteScreenHeight(data.screenHeight);
          }
          break;

        case "ERROR":
          // Todo erro do servidor inutiliza a sessão: encerra a conexão.
          closeSocket("Server error");
          resetSession();
          setStatus("error");
          setStatusMessage(translateServerError(data.message));
          break;

        default:
          console.log("Mensagem recebida do servidor:", data);
      }
    };

    socket.onerror = () => {
      if (socketRef.current !== socket) return;

      closeSocket("Socket error");
      resetSession();
      setStatus("error");
      setStatusMessage(
        "Não foi possível abrir a conexão. Verifique se o servidor está em execução e tente novamente."
      );
    };

    socket.onclose = (event: CloseEvent) => {
      if (socketRef.current !== socket) return;

      socketRef.current = null;
      resetSession();
      setStatus("disconnected");
      setStatusMessage(
        event.wasClean
          ? "Conexão encerrada."
          : "Conexão interrompida pelo servidor."
      );
    };
  }

  function disconnect() {
    attemptRef.current++;
    closeSocket("Client disconnected");
    resetSession();
    setStatus("disconnected");
    setStatusMessage("");
  }

  useEffect(() => {
    return () => {
      // Invalida uma conexão que ainda esteja esperando o token.
      attemptRef.current = -1;

      const socket = socketRef.current;
      socketRef.current = null;

      if (socket && socket.readyState < WebSocket.CLOSING) {
        socket.close(1000, "Component unmounted");
      }
    };
  }, []);

  return {
    status,
    statusMessage,
    remoteSessionId,
    hasFrame,
    canvasRef,
    remoteScreenWidth,
    remoteScreenHeight,
    fps,
    latencyMs,
    socketRef,
    connect,
    disconnect,
  };
}
