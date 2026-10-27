import { useCallback, useEffect, useRef, useState } from "react";
import type { PointerEvent as ReactPointerEvent, RefObject } from "react";
import {
  IDENTITY_VIEW,
  ZOOM_STEP,
  clampView,
  distance,
  getMaxZoom,
  midpoint,
  pinchView,
  zoomAt,
} from "../utils/zoom";
import type { Bounds, Point, ViewTransform } from "../utils/zoom";

type PinchState = {
  start: ViewTransform;
  startMid: Point;
  startDistance: number;
};

type PanState = {
  pointerId: number;
  startX: number;
  startY: number;
  start: ViewTransform;
};

function boundsOf(
  stage: HTMLElement | null,
  remoteWidth: number | null,
  remoteHeight: number | null
): Bounds | null {
  if (!stage) return null;

  const width = stage.clientWidth;
  const height = stage.clientHeight;

  return {
    width,
    height,
    maxZoom: getMaxZoom(width, height, remoteWidth, remoteHeight),
  };
}

/**
 * Zoom e pan da tela remota.
 *
 * - Celular: pinça com dois dedos (zoom e arrasto ao mesmo tempo).
 * - PC: botões de zoom, Ctrl + roda do mouse (ou pinça do trackpad) e o modo
 *   "Mover", que troca o arrasto do mouse por pan.
 *
 * Os handlers de ponteiro retornam `true` quando consumiram o evento; nesse
 * caso o chamador não deve enviá-lo ao computador remoto.
 */
export function useZoomPan(
  stageRef: RefObject<HTMLElement | null>,
  remoteWidth: number | null,
  remoteHeight: number | null
) {
  const [view, setViewState] = useState<ViewTransform>(IDENTITY_VIEW);
  const [panMode, setPanMode] = useState(false);

  // Espelho síncrono de `view`, para ler o valor atual dentro de handlers.
  const viewRef = useRef<ViewTransform>(IDENTITY_VIEW);
  const touchesRef = useRef(new Map<number, Point>());
  const pinchRef = useRef<PinchState | null>(null);
  const panRef = useRef<PanState | null>(null);
  // Após uma pinça, ignora o dedo que sobrou até todos saírem da tela.
  const suppressRef = useRef(false);

  const applyView = useCallback((next: ViewTransform) => {
    viewRef.current = next;
    setViewState(next);
  }, []);

  // Zoom com Ctrl + roda do mouse (também é o que a pinça do trackpad envia)
  // e reajuste ao redimensionar/girar a tela. O listener do wheel precisa ser
  // não-passivo para poder bloquear o zoom da página.
  useEffect(() => {
    const stage = stageRef.current;
    if (!stage) return;

    function handleWheel(event: WheelEvent) {
      if (!event.ctrlKey || !stage) return;
      event.preventDefault();

      const bounds = boundsOf(stage, remoteWidth, remoteHeight);
      if (!bounds) return;

      const rect = stage.getBoundingClientRect();
      const current = viewRef.current;

      applyView(
        zoomAt(
          current,
          current.scale * Math.exp(-event.deltaY * 0.002),
          { x: event.clientX - rect.left, y: event.clientY - rect.top },
          bounds
        )
      );
    }

    function handleResize() {
      const bounds = boundsOf(stage, remoteWidth, remoteHeight);
      if (bounds) applyView(clampView(viewRef.current, bounds));
    }

    stage.addEventListener("wheel", handleWheel, { passive: false });
    window.addEventListener("resize", handleResize);

    return () => {
      stage.removeEventListener("wheel", handleWheel);
      window.removeEventListener("resize", handleResize);
    };
  }, [stageRef, remoteWidth, remoteHeight, applyView]);

  function toStagePoint(clientX: number, clientY: number): Point {
    const rect = stageRef.current?.getBoundingClientRect();
    return { x: clientX - (rect?.left ?? 0), y: clientY - (rect?.top ?? 0) };
  }

  // Zoom pelos botões: em torno do centro da janela.
  function zoomBy(factor: number) {
    const bounds = boundsOf(stageRef.current, remoteWidth, remoteHeight);
    if (!bounds) return;

    const current = viewRef.current;
    const next = zoomAt(
      current,
      current.scale * factor,
      { x: bounds.width / 2, y: bounds.height / 2 },
      bounds
    );

    applyView(next);
    if (next.scale <= 1) setPanMode(false);
  }

  function resetZoom() {
    applyView(IDENTITY_VIEW);
    setPanMode(false);
  }

  const panActive = panMode && view.scale > 1;

  // (Re)inicia a pinça com os dois primeiros dedos que estão na tela.
  function startPinch() {
    const [a, b] = [...touchesRef.current.values()];
    if (!a || !b) return;

    const mid = midpoint(a, b);

    pinchRef.current = {
      start: viewRef.current,
      startMid: toStagePoint(mid.x, mid.y),
      startDistance: Math.max(distance(a, b), 1),
    };
  }

  function handlePointerDown(
    event: ReactPointerEvent,
    onGestureStart: () => void
  ): boolean {
    if (event.pointerType === "touch") {
      touchesRef.current.set(event.pointerId, {
        x: event.clientX,
        y: event.clientY,
      });

      if (touchesRef.current.size >= 2) {
        if (!pinchRef.current) {
          // O primeiro dedo já pode ter iniciado um clique/arrasto remoto.
          panRef.current = null;
          suppressRef.current = true;
          onGestureStart();
          startPinch();
        }

        event.currentTarget.setPointerCapture(event.pointerId);
        return true;
      }

      if (suppressRef.current) return true;
    }

    if (panActive) {
      panRef.current = {
        pointerId: event.pointerId,
        startX: event.clientX,
        startY: event.clientY,
        start: viewRef.current,
      };
      event.currentTarget.setPointerCapture(event.pointerId);
      return true;
    }

    return false;
  }

  function handlePointerMove(event: ReactPointerEvent): boolean {
    if (event.pointerType === "touch" && touchesRef.current.has(event.pointerId)) {
      touchesRef.current.set(event.pointerId, {
        x: event.clientX,
        y: event.clientY,
      });

      const pinch = pinchRef.current;

      if (pinch && touchesRef.current.size >= 2) {
        const bounds = boundsOf(stageRef.current, remoteWidth, remoteHeight);
        const [a, b] = [...touchesRef.current.values()];

        if (bounds && a && b) {
          const mid = midpoint(a, b);

          applyView(
            pinchView(
              pinch.start,
              pinch.startMid,
              pinch.startDistance,
              toStagePoint(mid.x, mid.y),
              distance(a, b),
              bounds
            )
          );
        }

        return true;
      }

      if (suppressRef.current) return true;
    }

    const pan = panRef.current;

    if (pan && pan.pointerId === event.pointerId) {
      const bounds = boundsOf(stageRef.current, remoteWidth, remoteHeight);

      if (bounds) {
        applyView(
          clampView(
            {
              scale: pan.start.scale,
              x: pan.start.x + (event.clientX - pan.startX),
              y: pan.start.y + (event.clientY - pan.startY),
            },
            bounds
          )
        );
      }

      return true;
    }

    // No modo "Mover" o mouse não deve mexer no cursor do PC remoto.
    return panActive;
  }

  // Serve para pointerup e pointercancel.
  function handlePointerUp(event: ReactPointerEvent): boolean {
    let consumed = false;

    if (event.pointerType === "touch") {
      touchesRef.current.delete(event.pointerId);

      if (pinchRef.current) {
        if (touchesRef.current.size >= 2) {
          startPinch();
        } else {
          pinchRef.current = null;
        }

        consumed = true;
      }

      if (suppressRef.current) {
        consumed = true;

        if (touchesRef.current.size === 0) {
          suppressRef.current = false;
        }
      }
    }

    if (panRef.current?.pointerId === event.pointerId) {
      panRef.current = null;
      consumed = true;
    }

    return consumed;
  }

  return {
    view,
    panActive,
    zoomIn: () => zoomBy(ZOOM_STEP),
    zoomOut: () => zoomBy(1 / ZOOM_STEP),
    resetZoom,
    togglePanMode: () => setPanMode((current) => !current),
    handlePointerDown,
    handlePointerMove,
    handlePointerUp,
  };
}
