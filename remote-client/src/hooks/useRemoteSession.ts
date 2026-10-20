import { useEffect, useRef, useState } from "react";

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

export interface RemoteSessionState {
  computerId: string;
  setComputerId: (id: string) => void;
  status: ConnectionStatus;
  statusMessage: string;
  remoteSessionId: string | null;
  screenImageUrl: string | null;
  remoteScreenWidth: number | null;
  remoteScreenHeight: number | null;
  framesReceived: number;
  socketRef: React.MutableRefObject<WebSocket | null>;
  connect: () => void;
  disconnect: () => void;
}

export function useRemoteSession(): RemoteSessionState {
  const [computerId, setComputerId] = useState("");
  const [status, setStatus] = useState<ConnectionStatus>("disconnected");
  const [statusMessage, setStatusMessage] = useState(
    "Informe o identificador do computador."
  );
  const [remoteSessionId, setRemoteSessionId] = useState<string | null>(null);
  const [screenImageUrl, setScreenImageUrl] = useState<string | null>(null);
  const [remoteScreenWidth, setRemoteScreenWidth] = useState<number | null>(
    null
  );
  const [remoteScreenHeight, setRemoteScreenHeight] = useState<number | null>(
    null
  );
  const [framesReceived, setFramesReceived] = useState(0);

  const imageUrlRef = useRef<string | null>(null);
  const socketRef = useRef<WebSocket | null>(null);

  function connect() {
    const id = computerId.trim();

    if (!id) {
      setStatus("error");
      setStatusMessage("Informe o identificador do computador.");
      return;
    }

    // Evita manter uma conexão anterior aberta.
    const previousSocket = socketRef.current;

    if (previousSocket) {
      socketRef.current = null;

      if (previousSocket.readyState < WebSocket.CLOSING) {
        previousSocket.close();
      }
    }

    setStatus("connecting");
    setStatusMessage("Conectando ao servidor...");
    setRemoteSessionId(null);

    // A conexão usa a origem atual; o Vite encaminha ao Spring Boot.
    const protocol =
      window.location.protocol === "https:" ? "wss:" : "ws:";

    const socket = new WebSocket(
      `${protocol}//${window.location.host}/api/v1/ws/client`
    );

    socket.binaryType = "arraybuffer";

    socketRef.current = socket;

    let connectionFailed = false;

    socket.onopen = () => {
      if (socketRef.current !== socket) return;

      setStatusMessage("Servidor conectado. Solicitando acesso...");

      socket.send(
        JSON.stringify({
          type: "CONNECT",
          computerId: id,
        })
      );
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
        setFramesReceived((previous) => previous + 1);

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
          setStatus("error");
          setStatusMessage(
            data.message ?? "Não foi possível conectar ao computador."
          );
          break;

        default:
          console.log("Mensagem recebida do servidor:", data);
      }
    };

    socket.onerror = () => {
      if (socketRef.current !== socket) return;

      connectionFailed = true;
      setStatus("error");
      setStatusMessage(
        "Falha no WebSocket. Verifique o servidor e o console do navegador."
      );
    };

    socket.onclose = (event: CloseEvent) => {
      if (socketRef.current !== socket) return;

      socketRef.current = null;

      if (!connectionFailed) {
        setStatus("disconnected");
        setStatusMessage(
          event.wasClean
            ? "Conexão encerrada."
            : "Conexão interrompida pelo servidor."
        );
      }
    };
  }

  function disconnect() {
    const socket = socketRef.current;
    socketRef.current = null;

    if (socket && socket.readyState < WebSocket.CLOSING) {
      socket.close(1000, "Client disconnected");
    }

    setStatus("disconnected");
    setStatusMessage("Conexão encerrada pelo usuário.");
    setRemoteSessionId(null);

    if (imageUrlRef.current) {
      URL.revokeObjectURL(imageUrlRef.current);
      imageUrlRef.current = null;
    }

    setScreenImageUrl(null);
    setFramesReceived(0);
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

      setScreenImageUrl(null);
      setFramesReceived(0);
    };
  }, []);

  return {
    computerId,
    setComputerId,
    status,
    statusMessage,
    remoteSessionId,
    screenImageUrl,
    remoteScreenWidth,
    remoteScreenHeight,
    framesReceived,
    socketRef,
    connect,
    disconnect,
  };
}

