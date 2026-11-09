import { ApiError, API_BASE, jsonRequest, readErrorMessage, request } from "./http";
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
      await readErrorMessage(response, "Confira os dados informados e tente novamente.")
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
