import { ApiError, API_BASE, jsonRequest, request, readErrorMessage } from "./http";
import { setAccessToken } from "../auth/token";

export interface RegisterInput {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
}

// Inicia o login com Google: o servidor redireciona para o Google e, no fim,
// de volta para o front em /login-success.
export const GOOGLE_LOGIN_URL = `${API_BASE}/oauth2/authorization/google`;

// O backend chama o e-mail de "username" no login.
export async function signIn(email: string, password: string): Promise<string> {
  const response = await jsonRequest("/auth/sign-in", {
    username: email,
    password,
  });

  if (response.status === 401 || response.status === 403) {
    throw new ApiError(response.status, "E-mail ou senha incorretos.");
  }

  if (!response.ok) {
    throw new ApiError(
      response.status,
      await readErrorMessage(response, "Não foi possível entrar. Tente novamente.")
    );
  }

  const data = (await response.json()) as { accessToken?: string };

  if (!data.accessToken) {
    throw new ApiError(500, "Resposta inválida do servidor.");
  }

  setAccessToken(data.accessToken);
  return data.accessToken;
}

export async function registerUser(input: RegisterInput): Promise<void> {
  const response = await jsonRequest("/auth/register", input);

  if (!response.ok) {
    throw new ApiError(
      response.status,
      await readRegisterErrorMessage(response)
    );
  }
}

// Apaga o cookie do refresh token no servidor e o token em memória.
export async function signOut(): Promise<void> {
  try {
    await request("/auth/logout", { method: "POST" });
  } finally {
    setAccessToken(null);
  }
}

/**
 * Trata a resposta de erro do GlobalExceptionHandler do servidor.
 * O backend devolve { field: message, ... } para erros de validação (400),
 * ou { message: "..." } para outros erros de negócio.
 */
async function readRegisterErrorMessage(response: Response): Promise<string> {
  try {
    const data: unknown = await response.json();

    if (data && typeof data === "object") {
      // Mapa de erros de validação: { email: "...", password: "...", ... }
      const entries = Object.entries(data as Record<string, unknown>).filter(
        ([, v]) => typeof v === "string"
      ) as [string, string][];

      if (entries.length > 0) {
        // Prioridade: mostrar o erro de senha primeiro, depois email, depois qualquer outro.
        const priority = ["password", "email", "firstName", "lastName"];
        entries.sort(
          ([a], [b]) =>
            (priority.indexOf(a) === -1 ? 99 : priority.indexOf(a)) -
            (priority.indexOf(b) === -1 ? 99 : priority.indexOf(b))
        );
        return entries.map(([, msg]) => msg).join(" ");
      }
    }
  } catch {
    // Corpo vazio ou não-JSON.
  }

  return "Confira os dados informados e tente novamente.";
}
