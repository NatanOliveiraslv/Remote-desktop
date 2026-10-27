import { useEffect, useRef, useState } from "react";
import type { RefObject } from "react";

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
  "Computer already has an active session":
    "Este computador já está em uso em outra sessão.",
  "Agent disconnected": "O Agent do computador foi desconectado.",
  "Invalid or inactive remote session": "A sessão remota não está mais ativa.",
  "Failed to initiate remote session":
    "Não foi possível iniciar a sessão remota.",
};

function translateServerError(message?: string): string {
  if (!message) return "Não foi possível conectar ao computador.";
  return SERVER_ERRORS[message] ?? message;
}

export interface RemoteSessionState {
  status: ConnectionStatus;
  statusMessage: string;
  remoteSessionId: string | null;
  screenImageUrl: string | null;
  remoteScreenWidth: number | null;
  remoteScreenHeight: number | null;
  socketRef: RefObject<WebSocket | null>;
  connect: (computerId: string) => void;
  disconnect: () => void;
}

export function useRemoteSession(): RemoteSessionState {
  const [status, setStatus] = useState<ConnectionStatus>("disconnected");
  const [statusMessage, setStatusMessage] = useState("");
  const [remoteSessionId, setRemoteSessionId] = useState<string | null>(null);
  const [screenImageUrl, setScreenImageUrl] = useState<string | null>(null);
  const [remoteScreenWidth, setRemoteScreenWidth] = useState<number | null>(
    null
  );
  const [remoteScreenHeight, setRemoteScreenHeight] = useState<number | null>(
    null
  );

  const imageUrlRef = useRef<string | null>(null);
  const socketRef = useRef<WebSocket | null>(null);

  // Fecha o socket atual. Zerar a ref antes faz os handlers antigos
  // (onclose/onmessage) ignorarem eventos de um socket descartado.
  function closeSocket(reason: string) {
    const socket = socketRef.current;
    socketRef.current = null;

    if (socket && socket.readyState < WebSocket.CLOSING) {
      socket.close(1000, reason);
    }
  }

  // Libera o último frame e limpa os dados da sessão anterior.
  function resetSession() {
    if (imageUrlRef.current) {
      URL.revokeObjectURL(imageUrlRef.current);
      imageUrlRef.current = null;
    }

    setScreenImageUrl(null);
    setRemoteSessionId(null);
    setRemoteScreenWidth(null);
    setRemoteScreenHeight(null);
  }

  function connect(computerId: string) {
    const id = computerId.trim();

    if (!id) {
      setStatus("error");
      setStatusMessage("Computador inválido.");
      return;
    }

    // Evita manter uma conexão anterior aberta.
    closeSocket("Starting a new connection");
    resetSession();

    setStatus("connecting");
    setStatusMessage("Conectando ao servidor...");

    // A conexão usa a origem atual; o Vite encaminha ao Spring Boot.
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";

    const socket = new WebSocket(
      `${protocol}//${window.location.host}/api/v1/ws/client`
    );

    socket.binaryType = "arraybuffer";
    socketRef.current = socket;

    socket.onopen = () => {
      if (socketRef.current !== socket) return;

      setStatusMessage("Servidor conectado. Solicitando acesso...");

      socket.send(JSON.stringify({ type: "CONNECT", computerId: id }));
    };

    socket.onmessage = (event: MessageEvent) => {
      if (socketRef.current !== socket) return;

      // Mensagem binária: imagem JPEG enviada pelo Agent.
      if (typeof event.data !== "string") {
        const blob =
          event.data instanceof Blob
            ? event.data
            : new Blob([event.data], { type: "image/jpeg" });

        const imageUrl = URL.createObjectURL(blob);

        // Libera o frame anterior para evitar acumular imagens na memória.
        if (imageUrlRef.current) {
          URL.revokeObjectURL(imageUrlRef.current);
        }

        imageUrlRef.current = imageUrl;
        setScreenImageUrl(imageUrl);

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
        "Não foi possível conectar ao servidor. Verifique se ele está em execução."
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
    closeSocket("Client disconnected");
    resetSession();
    setStatus("disconnected");
    setStatusMessage("");
  }

  useEffect(() => {
    return () => {
      const socket = socketRef.current;
      socketRef.current = null;

      if (socket && socket.readyState < WebSocket.CLOSING) {
        socket.close(1000, "Component unmounted");
      }

      if (imageUrlRef.current) {
        URL.revokeObjectURL(imageUrlRef.current);
        imageUrlRef.current = null;
      }
    };
  }, []);

  return {
    status,
    statusMessage,
    remoteSessionId,
    screenImageUrl,
    remoteScreenWidth,
    remoteScreenHeight,
    socketRef,
    connect,
    disconnect,
  };
}
