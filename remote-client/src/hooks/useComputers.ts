import { useCallback, useEffect, useState } from "react";
import { fetchComputers } from "../api/computers";
import type { Computer, PageMetadata } from "../api/computers";

export interface ComputersState {
  computers: Computer[];
  page: PageMetadata | null;
  pageNumber: number;
  setPageNumber: (page: number) => void;
  loading: boolean;
  error: string | null;
  refresh: () => void;
}

/**
 * Carrega a lista de computadores e a atualiza periodicamente para que o
 * status online/offline fique em dia. Use `enabled = false` para pausar
 * (por exemplo, durante uma sessão remota).
 */
export function useComputers(
  enabled: boolean,
  intervalMs = 5000
): ComputersState {
  const [computers, setComputers] = useState<Computer[]>([]);
  const [page, setPage] = useState<PageMetadata | null>(null);
  const [pageNumber, setPageNumber] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refreshTick, setRefreshTick] = useState(0);

  const refresh = useCallback(() => setRefreshTick((tick) => tick + 1), []);

  const handlePageChange = useCallback((newPage: number) => {
    setPageNumber(newPage);
    setLoading(true);
  }, []);

  useEffect(() => {
    if (!enabled) return;

    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout> | undefined;

    // O próximo ciclo só é agendado quando o anterior termina,
    // então nunca há requisições sobrepostas.
    async function load() {
      try {
        const response = await fetchComputers({
          page: pageNumber,
          signal: controller.signal,
        });
        if (controller.signal.aborted) return;

        setComputers(response.content);
        setPage(response.page);
        setError(null);
      } catch (err) {
        if (controller.signal.aborted) return;

        setError(
          err instanceof Error
            ? err.message
            : "Erro ao carregar os computadores."
        );
      }

      setLoading(false);
      timer = setTimeout(load, intervalMs);
    }

    load();

    return () => {
      controller.abort();
      clearTimeout(timer);
    };
  }, [enabled, intervalMs, refreshTick, pageNumber]);

  return {
    computers,
    page,
    pageNumber,
    setPageNumber: handlePageChange,
    loading,
    error,
    refresh,
  };
}
