import { useState } from "react";
import type { FormEvent } from "react";
import { GOOGLE_LOGIN_URL } from "../api/auth";
import { useAuth } from "../auth/auth-context";
import { GoogleIcon, MonitorIcon } from "./icons";

type Mode = "signin" | "signup";

export function LoginPage() {
  const auth = useAuth();

  const [mode, setMode] = useState<Mode>("signin");
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const isSignup = mode === "signup";

  function switchMode() {
    setMode(isSignup ? "signin" : "signup");
    setPassword("");
    setError(null);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;

    setSubmitting(true);
    setError(null);

    try {
      if (isSignup) {
        await auth.signUp({
          firstName: firstName.trim(),
          lastName: lastName.trim(),
          email: email.trim(),
          password,
        });
      } else {
        await auth.signIn(email.trim(), password);
      }
      // Em caso de sucesso a tela troca sozinha para a lista de computadores.
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Não foi possível concluir. Tente novamente."
      );
      setSubmitting(false);
    }
  }

  return (
    <main className="login-page">
      <div className="login-card">
        <div className="login-brand">
          <MonitorIcon size={26} className="brand-icon" />
          <span className="brand-name">Acesso Remoto</span>
        </div>

        <h1>{isSignup ? "Criar conta" : "Entrar"}</h1>

        {auth.notice && !error && <div className="notice">{auth.notice}</div>}

        {/* O login com Google é uma navegação completa: o servidor cuida do fluxo. */}
        <button
          type="button"
          className="btn btn-block btn-google"
          onClick={() => window.location.assign(GOOGLE_LOGIN_URL)}
        >
          <GoogleIcon />
          {isSignup ? "Cadastrar com o Google" : "Entrar com o Google"}
        </button>

        <div className="divider">ou</div>

        <form className="form" onSubmit={handleSubmit}>
          {isSignup && (
            <div className="form-row">
              <label className="field">
                Nome
                <input
                  type="text"
                  autoComplete="given-name"
                  placeholder="Maria"
                  required
                  value={firstName}
                  onChange={(e) => setFirstName(e.target.value)}
                />
              </label>

              <label className="field">
                Sobrenome
                <input
                  type="text"
                  autoComplete="family-name"
                  placeholder="Silva"
                  required
                  value={lastName}
                  onChange={(e) => setLastName(e.target.value)}
                />
              </label>
            </div>
          )}

          <label className="field">
            Email
            <input
              type="email"
              autoComplete="email"
              placeholder="voce@exemplo.com"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </label>

          <label className="field">
            Senha
            <input
              type="password"
              autoComplete={isSignup ? "new-password" : "current-password"}
              placeholder={isSignup ? "Crie uma senha" : "Sua senha"}
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </label>

          {error && (
            <div className="notice notice-error" role="alert">
              {error}
            </div>
          )}

          <button
            type="submit"
            className="btn btn-block btn-primary"
            disabled={submitting}
          >
            {submitting
              ? isSignup
                ? "Criando conta…"
                : "Entrando…"
              : isSignup
                ? "Criar conta"
                : "Entrar"}
          </button>
        </form>

        <p className="login-footer">
          {isSignup ? "Já tem uma conta? " : "Não tem uma conta? "}
          <button type="button" className="link-button" onClick={switchMode}>
            {isSignup ? "Entrar" : "Criar conta"}
          </button>
        </p>
      </div>
    </main>
  );
}
