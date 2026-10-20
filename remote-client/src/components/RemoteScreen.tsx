import { useEffect, useRef, useState } from "react";
import type { ConnectionStatus } from "../hooks/useRemoteSession";

interface RemoteScreenProps {
  status: ConnectionStatus;
  screenImageUrl: string | null;
  remoteSessionId: string | null;
  remoteScreenWidth: number | null;
  remoteScreenHeight: number | null;
  framesReceived: number;
  socketRef: React.MutableRefObject<WebSocket | null>;
}

// Converte o botão numérico do evento para o nome string do protocolo.
function getMouseButton(button: number): string | null {
  switch (button) {
    case 0:
      return "LEFT";
    case 1:
      return "MIDDLE";
    case 2:
      return "RIGHT";
    default:
      return null;
  }
}

// Converte coordenadas do evento para pixels do monitor remoto.
function toRemoteCoords(
  event: React.PointerEvent<HTMLImageElement>,
  screenWidth: number,
  screenHeight: number
): { x: number; y: number } | null {
  const rect = event.currentTarget.getBoundingClientRect();
  if (rect.width <= 0 || rect.height <= 0) return null;

  const relX = Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width));
  const relY = Math.max(0, Math.min(1, (event.clientY - rect.top) / rect.height));

  return {
    x: Math.min(screenWidth - 1, Math.floor(relX * screenWidth)),
    y: Math.min(screenHeight - 1, Math.floor(relY * screenHeight)),
  };
}

export function RemoteScreen({
  status,
  screenImageUrl,
  remoteSessionId,
  remoteScreenWidth,
  remoteScreenHeight,
  framesReceived,
  socketRef,
}: RemoteScreenProps) {
  const pressedMouseButtonRef = useRef<string | null>(null);
  const lastMouseMoveAtRef = useRef(0);
  const desktopViewRef = useRef<HTMLDivElement | null>(null);
  const [isFullscreen, setIsFullscreen] = useState(false);

  useEffect(() => {
    function handleFullscreenChange() {
      setIsFullscreen(
        document.fullscreenElement === desktopViewRef.current
      );
    }

    document.addEventListener("fullscreenchange", handleFullscreenChange);

    return () => {
      document.removeEventListener("fullscreenchange", handleFullscreenChange);
    };
  }, []);

  async function toggleFullscreen() {
    const element = desktopViewRef.current;

    if (!element) return;

    try {
      if (document.fullscreenElement === element) {
        await document.exitFullscreen();
      } else {
        await element.requestFullscreen();
      }
    } catch (error) {
      console.error("Erro ao alternar tela cheia:", error);
    }
  }

  function handleRemotePointerMove(
    event: React.PointerEvent<HTMLImageElement>
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

    const coords = toRemoteCoords(event, remoteScreenWidth, remoteScreenHeight);
    if (!coords) return;

    socket.send(
      JSON.stringify({
        type: "MOUSE_MOVE",
        remoteSessionId,
        x: coords.x,
        y: coords.y,
      })
    );
  }

  function handleRemotePointerDown(
    event: React.PointerEvent<HTMLImageElement>
  ) {
    if (
      status !== "connected" ||
      !remoteSessionId ||
      !remoteScreenWidth ||
      !remoteScreenHeight
    ) {
      return;
    }

    const socket = socketRef.current;

    if (!socket || socket.readyState !== WebSocket.OPEN) {
      return;
    }

    const button = getMouseButton(event.button);

    if (!button) {
      return;
    }

    const coords = toRemoteCoords(event, remoteScreenWidth, remoteScreenHeight);
    if (!coords) return;

    event.preventDefault();

    // Captura o ponteiro para permitir arrastar e soltar.
    event.currentTarget.setPointerCapture(event.pointerId);

    pressedMouseButtonRef.current = button;

    socket.send(
      JSON.stringify({
        type: "MOUSE_BUTTON_DOWN",
        remoteSessionId,
        button,
        x: coords.x,
        y: coords.y,
      })
    );
  }

  function handleRemotePointerUp(
    event: React.PointerEvent<HTMLImageElement>
  ) {
    const button = pressedMouseButtonRef.current;

    if (!button) {
      return;
    }

    const socket = socketRef.current;

    // Limpa o estado local mesmo se a conexão tiver sido perdida.
    pressedMouseButtonRef.current = null;

    if (
      !socket ||
      socket.readyState !== WebSocket.OPEN ||
      !remoteSessionId
    ) {
      return;
    }

    const coords =
      remoteScreenWidth && remoteScreenHeight
        ? toRemoteCoords(event, remoteScreenWidth, remoteScreenHeight)
        : null;

    socket.send(
      JSON.stringify({
        type: "MOUSE_BUTTON_UP",
        remoteSessionId,
        button,
        x: coords?.x ?? 0,
        y: coords?.y ?? 0,
      })
    );
  }

  // Corrige o bug do ponteiro travado: quando o pointerup não dispara
  // (troca de foco, saída do fullscreen via Escape, etc.), a captura
  // é perdida e este handler garante que o botão seja liberado remotamente.
  function handleLostPointerCapture() {
    const button = pressedMouseButtonRef.current;
    if (!button) return;
    pressedMouseButtonRef.current = null;

    const socket = socketRef.current;
    if (!socket || socket.readyState !== WebSocket.OPEN || !remoteSessionId)
      return;

    socket.send(
      JSON.stringify({
        type: "MOUSE_BUTTON_UP",
        remoteSessionId,
        button,
      })
    );
  }

  // Complementar ao handleLostPointerCapture para eventos de cancelamento
  // de ponteiro (e.g., scroll em dispositivos touch interferindo).
  function handlePointerCancel() {
    handleLostPointerCapture();
  }

  return (
    <section className="desktop-section">
      <div className="desktop-heading">
        <div>
          <h3>Tela remota</h3>
          <p>A tela do computador será exibida nesta área.</p>
        </div>

        <div className="desktop-actions">
          <span className={`connection-badge status-${status}`}>
            {status === "connected"
              ? "Conectado"
              : status === "connecting"
                ? "Conectando..."
                : status === "error"
                  ? "Erro na conexão"
                  : "Não conectado"}
          </span>

          <button
            type="button"
            className="fullscreen-button"
            onClick={toggleFullscreen}
            disabled={!screenImageUrl}
            aria-label={
              isFullscreen
                ? "Sair da tela cheia"
                : "Expandir tela remota"
            }
          >
            {isFullscreen ? "Sair da tela cheia" : "⛶ Tela cheia"}
          </button>
        </div>
      </div>

      <div
        className="desktop-view"
        ref={desktopViewRef}
      >
        {screenImageUrl ? (
          <img
            className="remote-screen-image"
            src={screenImageUrl}
            alt="Tela do computador remoto"
            draggable={false}
            style={{ touchAction: "none" }}
            onPointerMove={handleRemotePointerMove}
            onPointerDown={handleRemotePointerDown}
            onPointerUp={handleRemotePointerUp}
            onLostPointerCapture={handleLostPointerCapture}
            onPointerCancel={handlePointerCancel}
            onContextMenu={(e) => e.preventDefault()}
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
  );
}

