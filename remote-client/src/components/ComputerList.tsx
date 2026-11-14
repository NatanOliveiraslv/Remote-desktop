import type { Computer, PageMetadata } from "../api/computers";
import { ChevronIcon, MonitorIcon } from "./icons";

interface ComputerListProps {
  computers: Computer[];
  loading: boolean;
  error: string | null;
  onSelect: (computer: Computer) => void;
  onRetry: () => void;
  page?: PageMetadata | null;
  onPageChange?: (newPage: number) => void;
}

function describe(computer: Computer): string {
  return [
    computer.operatingSystem,
    computer.agentVersion && `Agent v${computer.agentVersion}`,
  ]
    .filter(Boolean)
    .join(" · ");
}

export function ComputerList({
  computers,
  loading,
  error,
  onSelect,
  onRetry,
  page,
  onPageChange,
}: ComputerListProps) {
  if (loading) {
    return (
      <ul className="pc-list" aria-busy="true" aria-label="Carregando computadores">
        {[0, 1, 2].map((i) => (
          <li key={i} className="pc-row pc-skeleton" />
        ))}
      </ul>
    );
  }

  if (computers.length === 0) {
    return (
      <div className="state-box" role={error ? "alert" : undefined}>
        <p>{error ?? "Nenhum computador cadastrado ainda."}</p>
        {page && page.number > 0 ? (
          <button
            type="button"
            className="btn btn-outline"
            onClick={() => onPageChange?.(0)}
          >
            Voltar para a primeira página
          </button>
        ) : error ? (
          <button type="button" className="btn btn-outline" onClick={onRetry}>
            Tentar novamente
          </button>
        ) : null}
      </div>
    );
  }

  return (
    <>
      {error && (
        <div className="notice notice-error" role="alert">
          {error} Exibindo a última lista carregada.
        </div>
      )}

      <ul className="pc-list">
        {computers.map((computer) => (
          <li key={computer.id}>
            <button
              type="button"
              className="pc-row"
              disabled={!computer.online}
              title={computer.online ? undefined : "Computador offline"}
              onClick={() => onSelect(computer)}
            >
              <span className="pc-icon">
                <MonitorIcon />
              </span>

              <span className="pc-info">
                <span className="pc-name">{computer.nameComputer}</span>
                <span className="pc-meta">{describe(computer)}</span>
              </span>

              <span
                className={`badge ${computer.online ? "badge-online" : "badge-offline"}`}
              >
                <span className="badge-dot" />
                {computer.online ? "Online" : "Offline"}
              </span>

              {computer.online && <ChevronIcon className="pc-chevron" size={20} />}
            </button>
          </li>
        ))}
      </ul>

      {page && page.totalPages > 1 && (
        <nav className="pagination" aria-label="Navegação entre páginas">
          <button
            type="button"
            className="btn btn-outline btn-sm"
            disabled={page.number <= 0 || loading}
            onClick={() => onPageChange?.(page.number - 1)}
          >
            Anterior
          </button>
          <span className="pagination-info">
            Página {page.number + 1} de {page.totalPages}
          </span>
          <button
            type="button"
            className="btn btn-outline btn-sm"
            disabled={page.number >= page.totalPages - 1 || loading}
            onClick={() => onPageChange?.(page.number + 1)}
          >
            Próxima
          </button>
        </nav>
      )}
    </>
  );
}
