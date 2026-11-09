// O access token fica só em memória (nunca em localStorage), então um script
// malicioso na página não consegue roubá-lo. Quem sobrevive a um F5 é o
// refresh token, guardado pelo servidor em um cookie HttpOnly.
let accessToken: string | null = null;

export function getAccessToken(): string | null {
  return accessToken;
}

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

interface JwtPayload {
  sub?: string;
  exp?: number;
}

// Lê o conteúdo (sem validar a assinatura, quem valida é o servidor).
export function decodeJwt(token: string): JwtPayload | null {
  try {
    const payload = token.split(".")[1];
    if (!payload) return null;

    const base64 = payload.replace(/-/g, "+").replace(/_/g, "/");
    const bytes = Uint8Array.from(atob(base64), (char) => char.charCodeAt(0));

    return JSON.parse(new TextDecoder().decode(bytes)) as JwtPayload;
  } catch {
    return null;
  }
}

// No backend, o "subject" do token é o e-mail do usuário.
export function getTokenSubject(token: string): string | null {
  return decodeJwt(token)?.sub ?? null;
}

// Verdadeiro se o token já expirou ou expira nos próximos `marginSeconds`.
// Serve só para renovar com antecedência: se não der para saber, o servidor
// decide (resposta 401 e renovação).
export function isTokenExpiring(token: string, marginSeconds = 30): boolean {
  const exp = decodeJwt(token)?.exp;
  if (!exp) return false;

  return exp * 1000 - Date.now() < marginSeconds * 1000;
}
