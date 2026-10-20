import type { ConnectionStatus } from "../hooks/useRemoteSession";

interface ConnectionCardProps {
  computerId: string;
  onComputerIdChange: (id: string) => void;
  status: ConnectionStatus;
  statusMessage: string;
  onConnect: () => void;
  onDisconnect: () => void;
}

export function ConnectionCard({
  computerId,
  onComputerIdChange,
  status,
  statusMessage,
  onConnect,
  onDisconnect,
}: ConnectionCardProps) {
  return (
    <section className="connection-card">
      <div className="card-heading">
        <div className="heading-icon">▣</div>

        <div>
          <h3>Nova conexão</h3>
          <p>
            Informe o identificador do computador que deseja acessar.
          </p>
        </div>
      </div>

      <label htmlFor="computerId">
        Identificador do computador
      </label>

      <input
        id="computerId"
        type="text"
        placeholder="Cole o computerId do Agent"
        value={computerId}
        onChange={(event) => onComputerIdChange(event.target.value)}
      />

      <button
        className="connect-button"
        onClick={status === "connected" ? onDisconnect : onConnect}
        disabled={
          status === "connecting" ||
          (status !== "connected" && !computerId.trim())
        }
      >
        {status === "connecting"
          ? "Conectando..."
          : status === "connected"
            ? "Desconectar"
            : "Conectar ao computador"}
      </button>

      <p className={`status-message status-${status}`} role="status">
        {statusMessage}
      </p>

      <p className="helper-text">
        O computador precisa estar ligado e com o Agent em execução.
      </p>
    </section>
  );
}

