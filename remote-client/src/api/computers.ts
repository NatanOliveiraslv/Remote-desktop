export interface Computer {
  id: string;
  nameComputer: string;
  operatingSystem: string;
  agentVersion: string;
  online: boolean;
}

// Em desenvolvimento o Vite encaminha /api para o Spring Boot
// (veja vite.config.ts), o que evita problemas de CORS.
const API_BASE = "/api/v1";

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
    response = await fetch(`${API_BASE}/computers`, {
      signal,
      headers: { Accept: "application/json" },
    });
  } catch (error) {
    // Requisições canceladas propagam o erro original para o chamador ignorar.
    if (signal?.aborted) throw error;
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
