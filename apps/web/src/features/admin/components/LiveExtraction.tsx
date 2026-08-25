import { useEffect, useState } from "react";
import { Clock01Icon, InboxIcon } from "@hugeicons/core-free-icons";
import { Icon } from "@/components/ui/icon";
import type { ActiveImport, IngestionPhase, IngestionStatus } from "@/features/admin/admin.service";

/** Libellés lisibles des phases d'import. */
const PHASE_LABEL: Record<IngestionPhase, string> = {
    "fetching-detail": "Lecture des métadonnées",
    "listing-chapters": "Liste des chapitres",
    downloading: "Téléchargement des chapitres",
    finishing: "Finalisation",
};

/** Durée écoulée « il y a … » compacte, mise à jour par le tick parent. */
function elapsed(fromIso: string, now: number): string {
    const secs = Math.max(0, Math.floor((now - new Date(fromIso).getTime()) / 1000));
    if (secs < 60) return `${secs}s`;
    const mins = Math.floor(secs / 60);
    if (mins < 60) return `${mins}m ${secs % 60}s`;
    return `${Math.floor(mins / 60)}h ${mins % 60}m`;
}

/**
 * Suivi LIVE de l'extraction — la pièce « mode console » du dashboard : import
 * actif avec barre de progression animée + pulse d'activité + chiffres mono, et
 * la file d'attente en dessous. Au repos, un état vide discret.
 */
export function LiveExtraction({ status }: { status: IngestionStatus | null }) {
    // Horloge locale pour rafraîchir « durée écoulée » chaque seconde, sans re-fetch.
    const [now, setNow] = useState(() => Date.now());
    useEffect(() => {
        const id = window.setInterval(() => setNow(Date.now()), 1000);
        return () => window.clearInterval(id);
    }, []);

    const active = status?.active ?? null;
    const queue = status?.queue ?? [];

    return (
        <section className="rounded-2xl border border-border bg-card">
            <div className="flex items-center justify-between border-b border-border px-5 py-4 sm:px-6">
                <div className="flex items-center gap-2.5">
                    <StatusDot active={active !== null} />
                    <h2 className="font-heading text-base font-bold">Extraction en direct</h2>
                </div>
                <span className="font-mono text-xs text-muted-foreground">
                    {active ? "ACTIF" : queue.length > 0 ? "EN FILE" : "AU REPOS"}
                </span>
            </div>

            <div className="p-5 sm:p-6">
                {active ? (
                    <ActiveCard active={active} now={now} />
                ) : (
                    <p className="py-6 text-center text-sm text-muted-foreground">
                        Aucune extraction en cours. Lance un import pour la voir apparaître ici.
                    </p>
                )}

                {queue.length > 0 && (
                    <div className="mt-5">
                        <p className="mb-2 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                            <Icon icon={InboxIcon} size={13} />
                            File d'attente · {queue.length}
                        </p>
                        <ul className="space-y-1.5">
                            {queue.map((q) => (
                                <li
                                    key={q.slug}
                                    className="flex items-center justify-between rounded-lg border border-border/70 bg-background/40 px-3 py-2"
                                >
                                    <span className="font-mono text-sm text-foreground">{q.slug}</span>
                                    <span className="flex items-center gap-1 text-xs text-muted-foreground">
                                        <Icon icon={Clock01Icon} size={12} />
                                        en attente
                                    </span>
                                </li>
                            ))}
                        </ul>
                    </div>
                )}
            </div>
        </section>
    );
}

/** Carte de l'import actif : titre, phase, barre, compteurs mono. */
function ActiveCard({ active, now }: { active: ActiveImport; now: number }) {
    const { title, slug, phase, done, total, skipped, startedAt } = active;
    const pct = total > 0 ? Math.min(100, Math.round((done / total) * 100)) : null;

    return (
        <div className="rounded-xl border border-primary/25 bg-primary/[0.06] p-4 sm:p-5">
            <div className="flex flex-wrap items-start justify-between gap-2">
                <div className="min-w-0">
                    <p className="truncate font-heading text-lg font-bold text-foreground">{title}</p>
                    <p className="truncate font-mono text-xs text-muted-foreground">{slug}</p>
                </div>
                <span className="shrink-0 rounded-full bg-primary/15 px-2.5 py-1 text-xs font-semibold text-primary">
                    {PHASE_LABEL[phase]}
                </span>
            </div>

            {/* Barre de progression (indéterminée tant que le total est inconnu). */}
            <div className="mt-4">
                <div className="mb-1.5 flex items-baseline justify-between font-mono text-sm">
                    <span className="text-foreground">
                        {done.toLocaleString("fr-FR")}
                        {total > 0 && (
                            <span className="text-muted-foreground"> / {total.toLocaleString("fr-FR")}</span>
                        )}
                        <span className="ml-1 text-xs text-muted-foreground">chapitres</span>
                    </span>
                    {pct !== null && <span className="text-primary">{pct}%</span>}
                </div>
                <div className="h-2 overflow-hidden rounded-full bg-secondary">
                    {pct !== null ? (
                        <div
                            className="h-full rounded-full bg-primary transition-[width] duration-700 ease-out"
                            style={{ width: `${pct}%` }}
                        />
                    ) : (
                        <div className="h-full w-1/3 animate-pulse rounded-full bg-primary/60" />
                    )}
                </div>
            </div>

            {/* Métriques secondaires en mono. */}
            <div className="mt-4 flex flex-wrap gap-x-6 gap-y-1 font-mono text-xs text-muted-foreground">
                <span>
                    <span className="text-foreground">{elapsed(startedAt, now)}</span> écoulées
                </span>
                {skipped > 0 && (
                    <span>
                        <span className="text-foreground">{skipped}</span> sautés
                    </span>
                )}
            </div>
        </div>
    );
}

/** Point d'état pulsant (vert animé si actif, gris fixe sinon). */
function StatusDot({ active }: { active: boolean }) {
    if (!active) {
        return <span className="size-2.5 rounded-full bg-muted-foreground/40" aria-hidden />;
    }
    return (
        <span className="relative flex size-2.5" aria-hidden>
            <span className="absolute inline-flex size-full animate-ping rounded-full bg-primary/70" />
            <span className="relative inline-flex size-2.5 rounded-full bg-primary" />
        </span>
    );
}
