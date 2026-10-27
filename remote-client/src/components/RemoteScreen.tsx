import { useEffect, useRef, useState } from "react";
import type { PointerEvent as ReactPointerEvent, RefObject } from "react";
import type { ConnectionStatus } from "../hooks/useRemoteSession";
import { useZoomPan } from "../hooks/useZoomPan";
import {
  ExpandIcon,
  MinusIcon,
  MoveIcon,
  PlusIcon,
  ShrinkIcon,
} from "./icons";

// Distância (px) que um toque precisa percorrer para virar arrasto, e não clique.
const TAP_SLOP = 10;

interface RemoteScreenProps {
  computerName: string;
  status: ConnectionStatus;
  statusMessage: string;
  screenImageUrl: string | null;
  remoteSessionId: string | null;
  remoteScreenWidth: number | null;
  remoteScreenHeight: number | null;
  socketRef: RefObject<WebSocket | null>;
  onDisconnect: () => void;
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

function clamp01(value: number): number {
  return Math.max(0, Math.min(1, value));
}

// Converte coordenadas do evento para pixels do monitor remoto.
// A imagem usa object-fit: contain; por isso descontamos as faixas vazias
// (letterbox) que aparecem quando a proporção da janela difere da remota.
function toRemoteCoords(
  event: ReactPointerEvent<HTMLImageElement>,
  screenWidth: number,
  screenHeight: number
): { x: number; y: number } | null {
  const rect = event.currentTarget.getBoundingClientRect();
  if (rect.width <= 0 || rect.height <= 0) return null;

  const scale = Math.min(rect.width / screenWidth, rect.height / screenHeight);
  const drawnWidth = screenWidth * scale;
  const drawnHeight = screenHeight * scale;
  const offsetX = (rect.width - drawnWidth) / 2;
  const offsetY = (rect.height - drawnHeight) / 2;

  const relX = clamp01((event.clientX - rect.left - offsetX) / drawnWidth);
  const relY = clamp01((event.clientY - rect.top - offsetY) / drawnHeight);

  return {
    x: Math.min(screenWidth - 1, Math.floor(relX * screenWidth)),
    y: Math.min(screenHeight - 1, Math.floor(relY * screenHeight)),
  };
}

/**
 * Sessão remota em tela cheia: mostra o carregamento até chegar o primeiro
 * frame e, depois, a tela do PC com uma barra de controles flutuante.
 */
export function RemoteScreen({
  computerName,
  status,
  statusMessage,
  screenImageUrl,
  remoteSessionId,
  remoteScreenWidth,
  remoteScreenHeight,
  socketRef,
  onDisconnect,
}: RemoteScreenProps) {
  const pressedMouseButtonRef = useRef<string | null>(null);
  const lastMouseMoveAtRef = useRef(0);
  // Toque ainda indeciso entre clique e arrasto (ou início de uma pinça).
  const touchPendingRef = useRef<{
    button: string;
    x: number;
    y: number;
    clientX: number;
    clientY: number;
  } | null>(null);
  const sessionRef = useRef<HTMLDivElement | null>(null);
  const [isFullscreen, setIsFullscreen] = useState(false);

  const zoom = useZoomPan(sessionRef, remoteScreenWidth, remoteScreenHeight);

  const canControl =
    status === "connected" &&
    !!remoteSessionId &&
    !!remoteScreenWidth &&
    !!remoteScreenHeight;

  useEffect(() => {
    function handleFullscreenChange() {
      setIsFullscreen(document.fullscreenElement === sessionRef.current);
    }

    document.addEventListener("fullscreenchange", handleFullscreenChange);

    return () => {
      document.removeEventListener("fullscreenchange", handleFullscreenChange);
    };
  }, []);

  async function toggleFullscreen() {
    const element = sessionRef.current;
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

  // Envia uma mensagem de controle ao Agent (via servidor).
  function send(message: Record<string, unknown>) {
    const socket = socketRef.current;

    if (!socket || socket.readyState !== WebSocket.OPEN || !remoteSessionId) {
      return;
    }

    socket.send(JSON.stringify({ ...message, remoteSessionId }));
  }

  // Libera o botão no PC remoto se ele estiver pressionado.
  function handleLostPointerCapture() {
    const button = pressedMouseButtonRef.current;
    if (!button) return;

    pressedMouseButtonRef.current = null;
    send({ type: "MOUSE_BUTTON_UP", button });
  }

  // Chamado quando uma pinça começa: o que o primeiro dedo estava fazendo
  // (clique pendente ou arrasto) é descartado, sem gerar clique indevido.
  function cancelMouseGesture() {
    touchPendingRef.current = null;
    handleLostPointerCapture();
  }

  function handlePointerDown(event: ReactPointerEvent<HTMLImageElement>) {
    if (zoom.handlePointerDown(event, cancelMouseGesture)) return;
    if (!canControl) return;

    const button = getMouseButton(event.button);
    if (!button) return;

    const coords = toRemoteCoords(
      event,
      remoteScreenWidth,
      remoteScreenHeight
    );
    if (!coords) return;

    event.preventDefault();

    // Captura o ponteiro para permitir arrastar e soltar.
    event.currentTarget.setPointerCapture(event.pointerId);

    // No toque, espera para saber se é clique, arrasto ou o início de uma
    // pinça; só então envia algo ao PC remoto.
    if (event.pointerType === "touch") {
      touchPendingRef.current = {
        button,
        x: coords.x,
        y: coords.y,
        clientX: event.clientX,
        clientY: event.clientY,
      };
      return;
    }

    pressedMouseButtonRef.current = button;

    send({ type: "MOUSE_BUTTON_DOWN", button, x: coords.x, y: coords.y });
  }

  function handlePointerMove(event: ReactPointerEvent<HTMLImageElement>) {
    if (zoom.handlePointerMove(event)) return;
    if (!canControl) return;

    const pending = touchPendingRef.current;

    if (pending) {
      const moved = Math.hypot(
        event.clientX - pending.clientX,
        event.clientY - pending.clientY
      );

      // Ainda dentro da folga: pode ser um toque simples ou uma pinça.
      if (moved < TAP_SLOP) return;

      // Virou arrasto: pressiona o botão na posição onde o dedo tocou.
      touchPendingRef.current = null;
      pressedMouseButtonRef.current = pending.button;

      send({
        type: "MOUSE_BUTTON_DOWN",
        button: pending.button,
        x: pending.x,
        y: pending.y,
      });
    }

    // Limita o envio a aproximadamente 30 mensagens por segundo.
    const now = performance.now();
    if (now - lastMouseMoveAtRef.current < 33) return;
    lastMouseMoveAtRef.current = now;

    const coords = toRemoteCoords(
      event,
      remoteScreenWidth,
      remoteScreenHeight
    );
    if (!coords) return;

    send({ type: "MOUSE_MOVE", x: coords.x, y: coords.y });
  }

  function handlePointerUp(event: ReactPointerEvent<HTMLImageElement>) {
    if (zoom.handlePointerUp(event)) return;

    // Toque simples (sem arrasto): vira um clique.
    const pending = touchPendingRef.current;

    if (pending) {
      touchPendingRef.current = null;

      send({
        type: "MOUSE_BUTTON_DOWN",
        button: pending.button,
        x: pending.x,
        y: pending.y,
      });
      send({
        type: "MOUSE_BUTTON_UP",
        button: pending.button,
        x: pending.x,
        y: pending.y,
      });
      return;
    }

    const button = pressedMouseButtonRef.current;
    if (!button) return;

    // Limpa o estado local mesmo se a conexão tiver sido perdida.
    pressedMouseButtonRef.current = null;

    const coords =
      remoteScreenWidth && remoteScreenHeight
        ? toRemoteCoords(event, remoteScreenWidth, remoteScreenHeight)
        : null;

    send({
      type: "MOUSE_BUTTON_UP",
      button,
      x: coords?.x ?? 0,
      y: coords?.y ?? 0,
    });
  }

  function handlePointerCancel(event: ReactPointerEvent<HTMLImageElement>) {
    zoom.handlePointerUp(event);
    touchPendingRef.current = null;
    handleLostPointerCapture();
  }

  const hasFrame = screenImageUrl !== null;

  return (
    <div
      ref={sessionRef}
      className={`session ${hasFrame ? "session-live" : ""}`}
      role="dialog"
      aria-modal="true"
      aria-label={`Sessão remota com ${computerName}`}
    >
      {hasFrame ? (
        <>
          <img
            className={`session-image ${zoom.panActive ? "session-image-pan" : ""}`}
            style={{
              transform: `translate(${zoom.view.x}px, ${zoom.view.y}px) scale(${zoom.view.scale})`,
            }}
            src={screenImageUrl}
            alt={`Tela de ${computerName}`}
            draggable={false}
            onPointerMove={handlePointerMove}
            onPointerDown={handlePointerDown}
            onPointerUp={handlePointerUp}
            onLostPointerCapture={handleLostPointerCapture}
            onPointerCancel={handlePointerCancel}
            onContextMenu={(event) => event.preventDefault()}
          />

          <div className="session-toolbar">
            <span className="badge-dot dot-online" />
            <span className="toolbar-name">{computerName}</span>
            <span className="toolbar-status">Conectado</span>

            <div className="zoom-group" role="group" aria-label="Zoom">
              <button
                type="button"
                className="btn-icon"
                onClick={zoom.zoomOut}
                disabled={zoom.view.scale <= 1}
                aria-label="Diminuir zoom"
              >
                <MinusIcon size={20} />
              </button>

              <button
                type="button"
                className="zoom-level"
                onClick={zoom.resetZoom}
                disabled={zoom.view.scale <= 1}
                aria-label="Restaurar zoom para 100%"
                title="Restaurar zoom"
              >
                {Math.round(zoom.view.scale * 100)}%
              </button>

              <button
                type="button"
                className="btn-icon"
                onClick={zoom.zoomIn}
                aria-label="Aumentar zoom"
              >
                <PlusIcon size={20} />
              </button>

              <button
                type="button"
                className="btn-icon"
                onClick={zoom.togglePanMode}
                disabled={zoom.view.scale <= 1}
                aria-pressed={zoom.panActive}
                aria-label="Mover a tela"
                title="Mover a tela (arrastar)"
              >
                <MoveIcon size={20} />
              </button>
            </div>

            <button
              type="button"
              className="btn-icon"
              onClick={toggleFullscreen}
              aria-label={isFullscreen ? "Sair da tela cheia" : "Tela cheia"}
            >
              {isFullscreen ? <ShrinkIcon size={20} /> : <ExpandIcon size={20} />}
            </button>

            <button type="button" className="btn btn-danger" onClick={onDisconnect}>
              Desconectar
            </button>
          </div>
        </>
      ) : (
        <div className="session-loading">
          <div className="spinner" role="status" aria-label="Carregando" />

          <div className="session-loading-text">
            <h2>Conectando a {computerName}</h2>
            <p>
              {status === "connected"
                ? "Conexão estabelecida. Aguardando a imagem da tela…"
                : statusMessage}
            </p>
          </div>

          <button type="button" className="btn btn-outline" onClick={onDisconnect}>
            Cancelar
          </button>
        </div>
      )}
    </div>
  );
}
