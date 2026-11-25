import { useState } from "react";
import type { Computer } from "../api/computers";
import { useAuth } from "../auth/auth-context";
import { useComputers } from "../hooks/useComputers";
import { useRemoteSession } from "../hooks/useRemoteSession";
import { ComputerList } from "./ComputerList";
import { RemoteScreen } from "./RemoteScreen";
import { MonitorIcon, RefreshIcon } from "./icons";

export function Dashboard() {
  const auth = useAuth();
  const session = useRemoteSession();
  const [selected, setSelected] = useState<Computer | null>(null);

  const inSession =
    session.status === "connecting" || session.status === "connected";

  // A lista só é atualizada enquanto não há uma sessão aberta.
  const { computers, page, loading, error, refresh, setPageNumber } =
    useComputers(!inSession);

  const onlineCount = computers.filter((computer) => computer.online).length;

  function handleSelect(computer: Computer) {
    setSelected(computer);
    void session.connect(computer.id);
  }

  const descriptionText = loading
    ? "Carregando computadores…"
    : page && page.totalPages > 1
      ? `${onlineCount} online nesta página · ${page.totalElements} computadores no total. Selecione um computador online para conectar.`
      : `${onlineCount} online · ${computers.length - onlineCount} offline. Selecione um computador online para conectar.`;

  return (
    <div className="app">
      {/* "inert" impede foco e cliques na lista atrás da sessão remota. */}
      <div className="app-content" inert={inSession}>
        <header className="app-header">
          <MonitorIcon size={26} className="brand-icon" />
          <span className="brand-name">Acesso Remoto</span>

          <div className="app-user">
            {auth.email && <span className="app-user-email">{auth.email}</span>}
            <button
              type="button"
              className="btn btn-outline btn-sm"
              onClick={() => void auth.signOut()}
            >
              Sair
            </button>
          </div>
        </header>

        <main className="page">
          <div className="page-heading">
            <div>
              <h1>Meus computadores</h1>
              <p className="page-description">{descriptionText}</p>
            </div>

            <button type="button" className="btn btn-outline" onClick={refresh}>
              <RefreshIcon size={18} />
              Atualizar
            </button>
          </div>

          {!inSession && session.statusMessage && (
            <div
              className={`notice ${session.status === "error" ? "notice-error" : ""}`}
              role={session.status === "error" ? "alert" : "status"}
            >
              {session.statusMessage}
            </div>
          )}

          <ComputerList
            computers={computers}
            page={page}
            onPageChange={setPageNumber}
            loading={loading}
            error={error}
            onSelect={handleSelect}
            onRetry={refresh}
          />
        </main>
      </div>

      {inSession && selected && (
        <RemoteScreen
          computerName={selected.nameComputer}
          status={session.status}
          statusMessage={session.statusMessage}
          hasFrame={session.hasFrame}
          canvasRef={session.canvasRef}
          remoteSessionId={session.remoteSessionId}
          remoteScreenWidth={session.remoteScreenWidth}
          remoteScreenHeight={session.remoteScreenHeight}
          fps={session.fps}
          latencyMs={session.latencyMs}
          socketRef={session.socketRef}
          onDisconnect={session.disconnect}
        />
      )}
    </div>
  );
}
