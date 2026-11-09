import { getAccessToken, isTokenExpiring, setAccessToken } from "../auth/token";

// Em desenvolvimento o Vite encaminha /api para o Spring Boot
// (veja vite.config.ts), então o front e a API ficam na mesma origem:
// sem CORS e com o cookie do refresh token indo junto automaticamente.
export const API_BASE = "/api/v1";

export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

const NETWORK_ERROR = "Não foi possível acessar o servidor.";

// Chamado quando a sessão não pode mais ser renovada (volta para o login).
let sessionExpiredHandler: (() => void) | null = null;

export function setSessionExpiredHandler(handler: (() => void) | null): void {
  sessionExpiredHandler = handler;
}

export function expireSession(): void {
  setAccessToken(null);
  sessionExpiredHandler?.();
}

/**
 * Requisição simples à API, sem token (login, cadastro, renovação...).
 * Falhas de rede viram `ApiError` com status 0; cancelamentos passam direto.
 */
export async function request(
  path: string,
  init: RequestInit = {}
): Promise<Response> {
  try {
    return await fetch(`${API_BASE}${path}`, {
      credentials: "same-origin",
      ...init,
    });
  } catch (error) {
    if (init.signal?.aborted) throw error;
    throw new ApiError(0, NETWORK_ERROR);
  }
}

export function jsonRequest(path: string, body: unknown): Promise<Response> {
  return request(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

// O backend devolve erros de negócio como { status, message }.
export async function readErrorMessage(
  response: Response,
  fallback: string
): Promise<string> {
  try {
    const data: unknown = await response.json();
    const message = (data as { message?: unknown } | null)?.message;

    if (typeof message === "string" && message) return message;
  } catch {
    // Corpo vazio ou que não é JSON.
  }

  return fallback;
}

let refreshInFlight: Promise<string | null> | null = null;

/**
 * Pede um novo access token usando o cookie do refresh token.
 * Várias chamadas ao mesmo tempo compartilham uma única requisição.
 * Devolve `null` se o refresh token for inválido ou tiver expirado.
 */
export function refreshAccessToken(): Promise<string | null> {
  refreshInFlight ??= doRefresh().finally(() => {
    refreshInFlight = null;
  });

  return refreshInFlight;
}

async function doRefresh(): Promise<string | null> {
  const response = await request("/auth/refresh-token", { method: "POST" });
  if (!response.ok) return null;

  const data = (await response.json()) as { accessToken?: string };
  if (!data.accessToken) return null;

  setAccessToken(data.accessToken);
  return data.accessToken;
}

/**
 * Token pronto para uso: o que está em memória, se ainda for válido, ou um
 * novo. Também é usado antes de abrir o WebSocket, que só confere o token
 * no momento da conexão.
 */
export async function getValidAccessToken(): Promise<string | null> {
  const current = getAccessToken();
  if (current && !isTokenExpiring(current)) return current;

  return refreshAccessToken();
}

/**
 * fetch autenticado: envia o token em `Authorization: Bearer` e, se o
 * servidor recusar (401), renova o token uma vez e repete a requisição.
 * Se não for possível renovar, encerra a sessão e lança `ApiError`.
 */
export async function apiFetch(
  path: string,
  init: RequestInit = {}
): Promise<Response> {
  const send = (token: string) => {
    const headers = new Headers(init.headers);
    headers.set("Authorization", `Bearer ${token}`);

    return request(path, { ...init, headers });
  };

  const token = await getValidAccessToken();
  if (!token) return failSession();

  const response = await send(token);
  if (response.status !== 401) return response;

  const renewed = await refreshAccessToken();
  if (!renewed) return failSession();

  const retry = await send(renewed);
  if (retry.status === 401) return failSession();

  return retry;
}

function failSession(): never {
  expireSession();
  throw new ApiError(401, "Sua sessão expirou. Entre novamente.");
}
