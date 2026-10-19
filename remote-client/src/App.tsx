import { useEffect, useRef, useState, type MouseEvent as ReactMouseEvent,} from "react";
import "./App.css";

type ConnectionStatus =
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

function App() {

  const [computerId, setComputerId] = useState("");
  const [status, setStatus] =
    useState<ConnectionStatus>("disconnected");
  const [statusMessage, setStatusMessage] =
    useState("Informe o identificador do computador.");
  const [remoteSessionId, setRemoteSessionId] =
    useState<string | null>(null);
  const [screenImageUrl, setScreenImageUrl] =
    useState<string | null>(null);
  const [remoteScreenWidth, setRemoteScreenWidth] =
    useState<number | null>(null);

  const [remoteScreenHeight, setRemoteScreenHeight] =
    useState<number | null>(null);

  const lastMouseMoveAtRef = useRef(0);

  const [framesReceived, setFramesReceived] = useState(0);

  const imageUrlRef = useRef<string | null>(null);

  const socketRef = useRef<WebSocket | null>(null);

  function handleConnect() {
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

  function handleDisconnect() {
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


  function handleRemoteMouseMove(
    event: ReactMouseEvent<HTMLImageElement>
  ) {
    const socket = socketRef.current;

    if (
      status !== "connected" ||
      !remoteSessionId ||
      !remoteScreenWidth ||
      !remoteScreenHeight ||
      !socket ||
      socket.readyState !== WebSocket.OPEN
    ) {
      return;
    }

    // Limita o envio a aproximadamente 30 mensagens por segundo.
    const now = performance.now();

    if (now - lastMouseMoveAtRef.current < 33) {
      return;
    }

    lastMouseMoveAtRef.current = now;

    const rect = event.currentTarget.getBoundingClientRect();

    if (rect.width === 0 || rect.height === 0) {
      return;
    }

    // Posição relativa do ponteiro dentro da imagem (0 a 1).
    const relativeX = (event.clientX - rect.left) / rect.width;
    const relativeY = (event.clientY - rect.top) / rect.height;

    // Converte para pixels do monitor remoto.
    const x = Math.max(
      0,
      Math.min(
        remoteScreenWidth - 1,
        Math.floor(relativeX * remoteScreenWidth)
      )
    );

    const y = Math.max(
      0,
      Math.min(
        remoteScreenHeight - 1,
        Math.floor(relativeY * remoteScreenHeight)
      )
    );

    socket.send(
      JSON.stringify({
        type: "MOUSE_MOVE",
        remoteSessionId,
        x,
        y,
      })
    );
  }


  return (
    <div className="app">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-icon">R</div>

          <div>
            <h1>RemoteAccess</h1>
            <span>Controle remoto</span>
          </div>
        </div>

        <nav>
          <div className="nav-item active">
            <span>▦</span>
            Computador remoto
          </div>
        </nav>

        <div className="sidebar-footer">
          <span className="status-dot" />
          Aguardando conexão
        </div>
      </aside>

      <main className="main">
        <header className="topbar">
          <div>
            <p className="eyebrow">ACESSO REMOTO</p>
            <h2>Conectar a um computador</h2>
            <p className="page-description">
              Acesse e controle seus computadores remotamente.
            </p>
          </div>

          <div className="user-avatar">U</div>
        </header>

        <section className="connection-card">
          <div className="card-heading">
            <div className="heading-icon">▣</div>

            <div>
              <h3>Nova conexão</h3>
              <p>
                Informe o identificador do computador que deseja acessar.
              </p>
            </div>
          </div>

          <label htmlFor="computerId">
            Identificador do computador
          </label>

          <input
            id="computerId"
            type="text"
            placeholder="Cole o computerId do Agent"
            value={computerId}
            onChange={(event) => setComputerId(event.target.value)}
          />

          <button
            className="connect-button"
            onClick={status === "connected" ? handleDisconnect : handleConnect}
            disabled={
              status === "connecting" ||
              (status !== "connected" && !computerId.trim())
            }
          >
            {status === "connecting"
              ? "Conectando..."
              : status === "connected"
                ? "Desconectar"
                : "Conectar ao computador"}
          </button>

          <p className={`status-message status-${status}`} role="status">
            {statusMessage}
          </p>

          <p className="helper-text">
            O computador precisa estar ligado e com o Agent em execução.
          </p>
        </section>

        <section className="desktop-section">
          <div className="desktop-heading">
            <div>
              <h3>Tela remota</h3>
              <p>A tela do computador será exibida nesta área.</p>
            </div>

            <span className={`connection-badge status-${status}`}>
              {status === "connected"
                ? "Conectado"
                : status === "connecting"
                  ? "Conectando..."
                  : status === "error"
                    ? "Erro na conexão"
                    : "Não conectado"}
            </span>
          </div>


          <div className="desktop-view">
            {screenImageUrl ? (
              <img
                className="remote-screen-image"
                src={screenImageUrl}
                alt="Tela do computador remoto"
                draggable={false}
                onMouseMove={handleRemoteMouseMove}
              />
            ) : (
              <div className="empty-screen">
                <div className="screen-icon">▱</div>

                <h3>
                  {status === "connected"
                    ? "Aguardando imagem do computador"
                    : "Nenhum computador conectado"}
                </h3>

                <p>
                  {status === "connected"
                    ? "A conexão foi estabelecida. Aguardando os frames da tela remota."
                    : "Informe o identificador acima para iniciar uma sessão remota."}
                </p>
              </div>
            )}
          </div>

          <p className="helper-text">
            Frames recebidos: {framesReceived}
          </p>

        </section>
      </main>
    </div>
  );
}

export default App;
