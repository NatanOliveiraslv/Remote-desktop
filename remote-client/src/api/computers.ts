export interface Computer {
  id: string;
  nameComputer: string;
  operatingSystem: string;
  agentVersion: string;
  online: boolean;
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
  signal?: AbortSignal
): Promise<Computer[]> {
  let response: Response;

  try {
    // apiFetch envia o token e o renova sozinho quando necessário.
    response = await apiFetch("/computers", {
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

  if (!Array.isArray(data)) {
    throw new Error("Resposta inesperada do servidor.");
  }

  return [...(data as Computer[])].sort(byStatusThenName);
}
