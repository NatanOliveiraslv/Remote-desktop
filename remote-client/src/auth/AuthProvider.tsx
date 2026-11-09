import { useCallback, useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import { registerUser, signIn as signInRequest, signOut as signOutRequest } from "../api/auth";
import type { RegisterInput } from "../api/auth";
import {
  ApiError,
  getValidAccessToken,
  setSessionExpiredHandler,
} from "../api/http";
import { AuthContext } from "./auth-context";
import type { AuthContextValue, AuthStatus } from "./auth-context";
import { getTokenSubject, setAccessToken } from "./token";

// Depois do login com Google, o servidor redireciona para
// /login-success?accessToken=... Lê o token e limpa a barra de endereço
// na hora, para ele não ficar no histórico do navegador.
function takeRedirectToken(): string | null {
  if (window.location.pathname !== "/login-success") return null;

  const token = new URLSearchParams(window.location.search).get("accessToken");
  window.history.replaceState(null, "", "/");

  return token;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>("loading");
  const [email, setEmail] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const startSession = useCallback((token: string) => {
    setEmail(getTokenSubject(token));
    setNotice(null);
    setStatus("authenticated");
  }, []);

  const endSession = useCallback((message: string | null) => {
    setAccessToken(null);
    setEmail(null);
    setNotice(message);
    setStatus("unauthenticated");
  }, []);

  // Ao abrir o app, recupera a sessão: usa o token do redirecionamento do
  // Google ou renova pelo cookie do refresh token (isso mantém o login após F5).
  useEffect(() => {
    let cancelled = false;

    async function restore() {
      const redirectToken = takeRedirectToken();
      if (redirectToken) setAccessToken(redirectToken);

      try {
        const token = await getValidAccessToken();
        if (cancelled) return;

        if (token) startSession(token);
        else endSession(null);
      } catch (error) {
        if (cancelled) return;

        endSession(
          error instanceof ApiError
            ? error.message
            : "Não foi possível acessar o servidor."
        );
      }
    }

    restore();

    return () => {
      cancelled = true;
    };
  }, [startSession, endSession]);

  // Se uma requisição descobrir que a sessão não pode ser renovada,
  // o app volta para a tela de login.
  useEffect(() => {
    setSessionExpiredHandler(() =>
      endSession("Sua sessão expirou. Entre novamente.")
    );

    return () => setSessionExpiredHandler(null);
  }, [endSession]);

  const signIn = useCallback(
    async (userEmail: string, password: string) => {
      startSession(await signInRequest(userEmail, password));
    },
    [startSession]
  );

  const signUp = useCallback(
    async (input: RegisterInput) => {
      await registerUser(input);
      // Depois de cadastrar, já entra com os mesmos dados.
      await signIn(input.email, input.password);
    },
    [signIn]
  );

  const signOut = useCallback(async () => {
    try {
      await signOutRequest();
    } catch {
      // Mesmo que o servidor não responda, a sessão local é encerrada.
    }

    endSession(null);
  }, [endSession]);

  const value = useMemo<AuthContextValue>(
    () => ({ status, email, notice, signIn, signUp, signOut }),
    [status, email, notice, signIn, signUp, signOut]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
