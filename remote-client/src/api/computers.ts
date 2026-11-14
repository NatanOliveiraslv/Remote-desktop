export interface Computer {
  id: string;
  nameComputer: string;
  operatingSystem: string;
  agentVersion: string;
  online: boolean;
}

export interface PageMetadata {
  size: number;
  number: number;
  totalElements: number;
  totalPages: number;
}

export interface ComputersResponse {
  content: Computer[];
  page: PageMetadata;
}

export interface FetchComputersOptions {
  page?: number;
  size?: number;
  signal?: AbortSignal;
}

import { ApiError, apiFetch } from "./http";

// Computadores online primeiro; dentro de cada grupo, ordem alfabética.
function byStatusThenName(a: Computer, b: Computer): number {
  return (
    Number(b.online) - Number(a.online) ||
    a.nameComputer.localeCompare(b.nameComputer, "pt-BR")
  );
}

export async function fetchComputers(
  optionsOrSignal?: FetchComputersOptions | AbortSignal
): Promise<ComputersResponse> {
  const options: FetchComputersOptions =
    optionsOrSignal instanceof AbortSignal
      ? { signal: optionsOrSignal }
      : optionsOrSignal ?? {};

  const { page, size, signal } = options;

  const queryParams = new URLSearchParams();
  if (page !== undefined) queryParams.set("page", String(page));
  if (size !== undefined) queryParams.set("size", String(size));

  const queryString = queryParams.toString() ? `?${queryParams.toString()}` : "";

  let response: Response;

  try {
    // apiFetch envia o token e o renova sozinho quando necessário.
    response = await apiFetch(`/computers${queryString}`, {
      signal,
      headers: { Accept: "application/json" },
    });
  } catch (error) {
    // Cancelamentos e erros da API (como sessão expirada) seguem como estão.
    if (signal?.aborted || error instanceof ApiError) throw error;
    throw new Error("Não foi possível acessar o servidor.", { cause: error });
  }

  if (!response.ok) {
    throw new Error(`O servidor respondeu com erro ${response.status}.`);
  }

  let data: unknown;

  try {
    data = await response.json();
  } catch {
    throw new Error("Resposta inválida do servidor.");
  }

  let content: Computer[];
  let pageMetadata: PageMetadata;

  if (
    data &&
    typeof data === "object" &&
    "content" in data &&
    Array.isArray((data as { content: unknown }).content)
  ) {
    const paged = data as { content: Computer[]; page?: Partial<PageMetadata> };
    content = paged.content;
    pageMetadata = {
      size: paged.page?.size ?? content.length,
      number: paged.page?.number ?? 0,
      totalElements: paged.page?.totalElements ?? content.length,
      totalPages: paged.page?.totalPages ?? (content.length > 0 ? 1 : 0),
    };
  } else if (Array.isArray(data)) {
    // Compatibilidade caso retorne array direto
    content = data as Computer[];
    pageMetadata = {
      size: content.length,
      number: 0,
      totalElements: content.length,
      totalPages: content.length > 0 ? 1 : 0,
    };
  } else {
    throw new Error("Resposta inesperada do servidor.");
  }

  return {
    content: [...content].sort(byStatusThenName),
    page: pageMetadata,
  };
}
