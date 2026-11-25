/**
 * Parses the binary dirty-tile frame format produced by the Agent's
 * DirtyRectEngine and paints dirty tiles onto a canvas.
 *
 * Binary format (see DirtyRectEngine.java class javadoc):
 *
 *   Header (16 bytes):
 *     [8]  captureTimestamp   – epoch millis
 *     [2]  frameWidth         – uint16 BE
 *     [2]  frameHeight        – uint16 BE
 *     [2]  tileSize           – uint16 BE
 *     [1]  flags              – bit 0 = isKeyframe
 *     [1]  tileCount          – 0-255
 *
 *   Per tile (10 + N bytes):
 *     [1]  col
 *     [1]  row
 *     [2]  tilePixelWidth     – uint16 BE
 *     [2]  tilePixelHeight    – uint16 BE
 *     [4]  jpegLength         – uint32 BE
 *     [N]  jpegData
 */

const HEADER_SIZE = 16;

export interface DirtyTile {
  col: number;
  row: number;
  pixelWidth: number;
  pixelHeight: number;
  jpeg: Blob;
}

export interface TileFrame {
  captureTimestamp: number;
  frameWidth: number;
  frameHeight: number;
  tileSize: number;
  isKeyframe: boolean;
  tiles: DirtyTile[];
}

/**
 * Parses the binary payload into a structured TileFrame.
 * Returns `null` if the payload is too short or malformed.
 */
export function parseTileFrame(buffer: ArrayBuffer): TileFrame | null {
  if (buffer.byteLength < HEADER_SIZE) return null;

  const view = new DataView(buffer);
  let offset = 0;

  const captureTimestamp = Number(view.getBigUint64(offset));
  offset += 8;
  const frameWidth = view.getUint16(offset);
  offset += 2;
  const frameHeight = view.getUint16(offset);
  offset += 2;
  const tileSize = view.getUint16(offset);
  offset += 2;
  const flags = view.getUint8(offset);
  offset += 1;
  const tileCount = view.getUint8(offset);
  offset += 1;

  const isKeyframe = (flags & 1) !== 0;

  if (frameWidth === 0 || frameHeight === 0 || tileSize === 0) return null;

  const tiles: DirtyTile[] = [];

  for (let i = 0; i < tileCount; i++) {
    if (offset + 10 > buffer.byteLength) {
      console.warn(`Tile frame truncated at tile ${i}/${tileCount}`);
      break;
    }

    const col = view.getUint8(offset);
    offset += 1;
    const row = view.getUint8(offset);
    offset += 1;
    const pixelWidth = view.getUint16(offset);
    offset += 2;
    const pixelHeight = view.getUint16(offset);
    offset += 2;
    const jpegLength = view.getUint32(offset);
    offset += 4;

    if (offset + jpegLength > buffer.byteLength) {
      console.warn(`Tile frame truncated: JPEG data for tile ${i} overflows buffer`);
      break;
    }

    const jpegSlice = buffer.slice(offset, offset + jpegLength);
    const jpeg = new Blob([jpegSlice], { type: "image/jpeg" });
    offset += jpegLength;

    tiles.push({ col, row, pixelWidth, pixelHeight, jpeg });
  }

  return { captureTimestamp, frameWidth, frameHeight, tileSize, isKeyframe, tiles };
}

/**
 * Decodes and paints all dirty tiles onto the target canvas 2D context.
 * Resizes the canvas if the frame dimensions changed.
 */
export async function paintTilesToCanvas(
  canvas: HTMLCanvasElement,
  ctx: CanvasRenderingContext2D,
  frame: TileFrame
): Promise<void> {
  if (canvas.width !== frame.frameWidth || canvas.height !== frame.frameHeight) {
    canvas.width = frame.frameWidth;
    canvas.height = frame.frameHeight;
  }

  // Decode all tile JPEGs in parallel, then draw each at its grid position.
  const decoded = await Promise.all(
    frame.tiles.map(async (tile) => ({
      tile,
      bitmap: await createImageBitmap(tile.jpeg),
    }))
  );

  for (const { tile, bitmap } of decoded) {
    const x = tile.col * frame.tileSize;
    const y = tile.row * frame.tileSize;
    ctx.drawImage(bitmap, x, y, tile.pixelWidth, tile.pixelHeight);
    bitmap.close();
  }
}
