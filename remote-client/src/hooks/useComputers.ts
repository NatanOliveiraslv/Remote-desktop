import { useCallback, useEffect, useState } from "react";
import { fetchComputers } from "../api/computers";
import type { Computer } from "../api/computers";

export interface ComputersState {
  computers: Computer[];
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
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refreshTick, setRefreshTick] = useState(0);

  const refresh = useCallback(() => setRefreshTick((tick) => tick + 1), []);

  useEffect(() => {
    if (!enabled) return;

    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout> | undefined;

    // O próximo ciclo só é agendado quando o anterior termina,
    // então nunca há requisições sobrepostas.
    async function load() {
      try {
        const data = await fetchComputers(controller.signal);
        if (controller.signal.aborted) return;

        setComputers(data);
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
  }, [enabled, intervalMs, refreshTick]);

  return { computers, loading, error, refresh };
}
