// Matemática de zoom e pan da tela remota.
//
// A imagem ocupa todo o "palco" (a janela da sessão) e recebe um
// `transform: translate(x, y) scale(scale)` com origem no canto superior
// esquerdo. Assim, `x` e `y` são coordenadas do palco, em pixels.

export interface Point {
  x: number;
  y: number;
}

export interface ViewTransform {
  scale: number;
  x: number;
  y: number;
}

// Tamanho do palco e o zoom máximo permitido para ele.
export interface Bounds {
  width: number;
  height: number;
  maxZoom: number;
}

export const IDENTITY_VIEW: ViewTransform = { scale: 1, x: 0, y: 0 };

export const MIN_ZOOM = 1;
export const ZOOM_STEP = 1.25;

const DEFAULT_MAX_ZOOM = 4;
const ABSOLUTE_MAX_ZOOM = 10;

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

export function distance(a: Point, b: Point): number {
  return Math.hypot(a.x - b.x, a.y - b.y);
}

export function midpoint(a: Point, b: Point): Point {
  return { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 };
}

/**
 * Zoom máximo: permite chegar a 2x o tamanho real (1 pixel remoto = 2 pixels
 * da tela). Em um celular exibindo um monitor 1920 px isso passa de 4x,
 * então o limite sobe até 10x.
 */
export function getMaxZoom(
  stageWidth: number,
  stageHeight: number,
  remoteWidth: number | null,
  remoteHeight: number | null
): number {
  if (!stageWidth || !stageHeight || !remoteWidth || !remoteHeight) {
    return DEFAULT_MAX_ZOOM;
  }

  // Pixels exibidos por pixel remoto quando a tela está ajustada à janela.
  const fit = Math.min(stageWidth / remoteWidth, stageHeight / remoteHeight);

  return clamp(2 / fit, DEFAULT_MAX_ZOOM, ABSOLUTE_MAX_ZOOM);
}

// Mantém o zoom no intervalo permitido e a imagem cobrindo todo o palco.
export function clampView(view: ViewTransform, bounds: Bounds): ViewTransform {
  const scale = clamp(view.scale, MIN_ZOOM, bounds.maxZoom);

  return {
    scale,
    x: clamp(view.x, bounds.width * (1 - scale), 0),
    y: clamp(view.y, bounds.height * (1 - scale), 0),
  };
}

// Aplica um novo zoom mantendo o ponto focal (coordenadas do palco) parado.
export function zoomAt(
  view: ViewTransform,
  nextScale: number,
  focal: Point,
  bounds: Bounds
): ViewTransform {
  const scale = clamp(nextScale, MIN_ZOOM, bounds.maxZoom);
  const ratio = scale / view.scale;

  return clampView(
    {
      scale,
      x: focal.x - (focal.x - view.x) * ratio,
      y: focal.y - (focal.y - view.y) * ratio,
    },
    bounds
  );
}

/**
 * Gesto de pinça: o ponto da imagem que estava sob o centro dos dedos no
 * início continua sob o centro atual (isso faz o zoom e o arrasto juntos).
 */
export function pinchView(
  start: ViewTransform,
  startMid: Point,
  startDistance: number,
  mid: Point,
  currentDistance: number,
  bounds: Bounds
): ViewTransform {
  const scale = clamp(
    start.scale * (currentDistance / startDistance),
    MIN_ZOOM,
    bounds.maxZoom
  );
  const ratio = scale / start.scale;

  return clampView(
    {
      scale,
      x: mid.x - (startMid.x - start.x) * ratio,
      y: mid.y - (startMid.y - start.y) * ratio,
    },
    bounds
  );
}
