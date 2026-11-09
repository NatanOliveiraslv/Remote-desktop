import { createContext, useContext } from "react";
import type { RegisterInput } from "../api/auth";

export type AuthStatus = "loading" | "authenticated" | "unauthenticated";

export interface AuthContextValue {
  status: AuthStatus;
  // E-mail do usuário logado (vem do token).
  email: string | null;
  // Aviso para a tela de login (por exemplo, "sua sessão expirou").
  notice: string | null;
  signIn: (email: string, password: string) => Promise<void>;
  signUp: (input: RegisterInput) => Promise<void>;
  signOut: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);

  if (!context) {
    throw new Error("useAuth precisa ser usado dentro de <AuthProvider>.");
  }

  return context;
}
