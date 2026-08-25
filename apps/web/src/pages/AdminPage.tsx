import { useCallback, useState } from "react";
import { Settings02Icon, DatabaseIcon, FlashIcon, InboxIcon } from "@hugeicons/core-free-icons";
import AppLayout from "@/components/ui/AppLayout";
import { Icon } from "@/components/ui/icon";
import { IngestionModule } from "@/features/admin/components/IngestionModule";
import { LiveExtraction } from "@/features/admin/components/LiveExtraction";
import { useIngestionStatus } from "@/features/admin/hooks/useIngestionStatus";
import type { IconSvgElement } from "@hugeicons/react";

/**
 * Tableau de bord d'administration. Direction hybride : une base éditoriale
 * épurée (cohérente avec l'app) et une section « extraction en direct » en mode
 * console (progression animée, pulse, chiffres mono). Premier module : Ingestion.
 * La page est structurée pour accueillir d'autres modules plus tard.
 *
 * L'accès est gardé par `AdminRoute` (et le back renvoie 403 aux non-admins).
 */
export default function AdminPage() {
    // Un compteur qui, incrémenté, force le suivi live à se rafraîchir tout de
    // suite après qu'on a lancé un import/sync.
    const [refreshKey, setRefreshKey] = useState(0);
    const bump = useCallback(() => setRefreshKey((k) => k + 1), []);
    const { status } = useIngestionStatus(refreshKey);

    const active = status?.active ?? null;
    const queueLen = status?.queue.length ?? 0;

    return (
        <AppLayout>
            <div className="mx-auto max-w-5xl px-4 py-8 sm:px-6 sm:py-10">
                {/* Hero */}
                <header className="relative mb-8 overflow-hidden rounded-3xl border border-border bg-linear-to-br from-secondary/60 via-card to-card p-6 sm:p-8">
                    <div className="pointer-events-none absolute -right-16 -top-16 size-56 rounded-full bg-primary/10 blur-3xl" />
                    <div className="relative flex items-center gap-4">
                        <span className="grid size-12 place-items-center rounded-2xl bg-linear-to-br from-primary to-primary-active text-primary-foreground shadow-lg shadow-primary/30">
                            <Icon icon={Settings02Icon} size={24} />
                        </span>
                        <div>
                            <h1 className="font-heading text-2xl font-extrabold tracking-tight">
                                Administration
                            </h1>
                            <p className="text-sm text-muted-foreground">
                                Pilotage de l'ingestion et des outils internes.
                            </p>
                        </div>
                    </div>

                    {/* Bandeau de stats live */}
                    <div className="relative mt-6 grid grid-cols-2 gap-3 sm:grid-cols-3">
                        <StatCard
                            icon={FlashIcon}
                            label="Extraction"
                            value={active ? "En cours" : "Au repos"}
                            live={active !== null}
                        />
                        <StatCard
                            icon={InboxIcon}
                            label="File d'attente"
                            value={queueLen === 0 ? "Vide" : `${queueLen} titre${queueLen > 1 ? "s" : ""}`}
                        />
                        <StatCard
                            icon={DatabaseIcon}
                            label="Source"
                            value="chikari"
                            className="col-span-2 sm:col-span-1"
                        />
                    </div>
                </header>

                {/* Suivi live (mode console) */}
                <div className="mb-6">
                    <LiveExtraction status={status} />
                </div>

                {/* Modules d'action */}
                <div className="space-y-6">
                    <IngestionModule onLaunched={bump} />
                </div>
            </div>
        </AppLayout>
    );
}

/** Petite carte de statistique du hero. `live` allume un point pulsant. */
function StatCard({
    icon,
    label,
    value,
    live = false,
    className = "",
}: {
    icon: IconSvgElement;
    label: string;
    value: string;
    live?: boolean;
    className?: string;
}) {
    return (
        <div className={`rounded-xl border border-border bg-background/40 p-3.5 ${className}`}>
            <div className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
                <Icon icon={icon} size={14} />
                {label}
            </div>
            <div className="mt-1 flex items-center gap-2">
                {live && (
                    <span className="relative flex size-2" aria-hidden>
                        <span className="absolute inline-flex size-full animate-ping rounded-full bg-primary/70" />
                        <span className="relative inline-flex size-2 rounded-full bg-primary" />
                    </span>
                )}
                <span className="font-heading text-base font-bold text-foreground">{value}</span>
            </div>
        </div>
    );
}
