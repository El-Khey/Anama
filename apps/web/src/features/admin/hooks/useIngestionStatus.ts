import { useEffect, useRef, useState } from "react";
import { getStatus, type IngestionStatus } from "@/features/admin/admin.service";

/**
 * Interroge périodiquement l'état de l'ingestion (import en cours + file).
 *
 * <p>Cadence adaptative : 2 s quand quelque chose tourne (import actif ou file
 * non vide), 5 s au repos — inutile de marteler l'API quand rien ne se passe.
 * Le polling s'arrête au démontage. Une erreur ponctuelle n'efface pas le
 * dernier état connu (évite les clignotements).
 *
 * @param refreshKey change cette valeur pour forcer un rafraîchissement immédiat
 *                   (ex. juste après avoir lancé un import).
 */
export function useIngestionStatus(refreshKey: number) {
    const [status, setStatus] = useState<IngestionStatus | null>(null);
    const [error, setError] = useState<string | null>(null);
    const timerRef = useRef<number | null>(null);

    useEffect(() => {
        let cancelled = false;

        async function tick() {
            try {
                const s = await getStatus();
                if (cancelled) return;
                setStatus(s);
                setError(null);
                // Actif ou file non vide → on suit de près ; sinon on lève le pied.
                const busy = s.active !== null || s.queue.length > 0;
                schedule(busy ? 2000 : 5000);
            } catch (err) {
                if (cancelled) return;
                setError(err instanceof Error ? err.message : "Statut indisponible");
                schedule(5000); // on retente, sans effacer le dernier état connu
            }
        }

        function schedule(delay: number) {
            if (cancelled) return;
            timerRef.current = window.setTimeout(tick, delay);
        }

        tick();
        return () => {
            cancelled = true;
            if (timerRef.current !== null) window.clearTimeout(timerRef.current);
        };
    }, [refreshKey]);

    return { status, error };
}
