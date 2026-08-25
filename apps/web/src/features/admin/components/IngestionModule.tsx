import { useState } from "react";
import { BookOpen01Icon, RefreshIcon, SparklesIcon } from "@hugeicons/core-free-icons";
import { Icon } from "@/components/ui/icon";
import { Button } from "@/components/ui/button";
import { ApiError } from "@/lib/http";
import { importNovel, triggerSync } from "@/features/admin/admin.service";

/** Retour d'action affiché en ligne (vert = ok, rouge = erreur), façon le reste de l'app. */
type Status = { ok: boolean; message: string } | null;

/** Traduit une erreur d'appel admin en message lisible (403/503 gérés finement). */
function messageFromError(err: unknown): string {
    if (err instanceof ApiError) {
        if (err.status === 403) return "Accès refusé : ton compte n'est pas administrateur.";
        if (err.status === 503) return "La source d'ingestion est injoignable pour le moment.";
        return err.message;
    }
    return err instanceof Error ? err.message : "Une erreur est survenue.";
}

/**
 * Module Ingestion de la page Admin : importer un titre précis depuis la source
 * (chikari) et déclencher un cycle de synchronisation complet. Les deux actions
 * partent en arrière-plan côté serveur (202 Accepted) — on confirme la prise en
 * compte, et le suivi live (au-dessus) montre la progression.
 *
 * @param onLaunched appelé après un déclenchement réussi, pour rafraîchir
 *                   immédiatement le suivi live (sans attendre le prochain tick).
 */
export function IngestionModule({ onLaunched }: { onLaunched?: () => void }) {
    const [slug, setSlug] = useState("");
    const [importing, setImporting] = useState(false);
    const [syncing, setSyncing] = useState(false);
    const [importStatus, setImportStatus] = useState<Status>(null);
    const [syncStatus, setSyncStatus] = useState<Status>(null);

    const slugTrimmed = slug.trim();

    async function handleImport(e: React.FormEvent) {
        e.preventDefault();
        if (!slugTrimmed || importing) return;
        setImporting(true);
        setImportStatus(null);
        try {
            const res = await importNovel(slugTrimmed);
            setImportStatus({ ok: true, message: res.message });
            setSlug("");
            onLaunched?.();
        } catch (err) {
            setImportStatus({ ok: false, message: messageFromError(err) });
        } finally {
            setImporting(false);
        }
    }

    async function handleSync() {
        if (syncing) return;
        setSyncing(true);
        setSyncStatus(null);
        try {
            const res = await triggerSync();
            setSyncStatus({ ok: true, message: res.message });
            onLaunched?.();
        } catch (err) {
            setSyncStatus({ ok: false, message: messageFromError(err) });
        } finally {
            setSyncing(false);
        }
    }

    return (
        <section className="rounded-2xl border border-border bg-card">
            {/* En-tête du module */}
            <div className="flex items-start gap-3 border-b border-border p-5 sm:p-6">
                <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-primary/15 text-primary">
                    <Icon icon={SparklesIcon} size={20} />
                </span>
                <div>
                    <h2 className="font-heading text-base font-bold">Ingestion</h2>
                    <p className="mt-0.5 text-sm text-muted-foreground">
                        Rapatrie les light novels depuis la source. Le job planifié tourne seul ;
                        ici, tu peux forcer un titre ou lancer une synchronisation à la demande.
                    </p>
                </div>
            </div>

            <div className="space-y-6 p-5 sm:p-6">
                {/* Importer un titre précis */}
                <form onSubmit={handleImport} className="space-y-2">
                    <label htmlFor="admin-slug" className="text-sm font-medium">
                        Importer un titre
                    </label>
                    <div className="flex flex-col gap-2 sm:flex-row">
                        <input
                            id="admin-slug"
                            value={slug}
                            onChange={(e) => setSlug(e.target.value)}
                            placeholder="slug du roman (ex. shadow-slave)"
                            autoCapitalize="none"
                            autoCorrect="off"
                            spellCheck={false}
                            className="h-10 w-full rounded-md border border-border bg-input/30 px-3 text-sm outline-none transition-colors focus:border-ring/60"
                        />
                        <Button
                            type="submit"
                            disabled={!slugTrimmed || importing}
                            className="shrink-0"
                            data-icon="inline-start"
                        >
                            <Icon icon={BookOpen01Icon} size={16} />
                            {importing ? "Import…" : "Importer"}
                        </Button>
                    </div>
                    <p className="text-xs text-muted-foreground">
                        Le slug est la fin de l'URL du roman sur la source. L'import se fait en
                        arrière-plan (peut durer plusieurs minutes pour un gros titre).
                    </p>
                    {importStatus && (
                        <p
                            role="status"
                            className={`text-sm ${importStatus.ok ? "text-up" : "text-destructive"}`}
                        >
                            {importStatus.message}
                        </p>
                    )}
                </form>

                <div className="h-px bg-border" />

                {/* Synchronisation complète */}
                <div className="space-y-2">
                    <p className="text-sm font-medium">Synchronisation complète</p>
                    <p className="text-xs text-muted-foreground">
                        Découvre de nouveaux titres et met à jour les chapitres des romans déjà en
                        base. Sans effet si un cycle est déjà en cours.
                    </p>
                    <Button
                        type="button"
                        variant="secondary"
                        onClick={handleSync}
                        disabled={syncing}
                        data-icon="inline-start"
                    >
                        <Icon icon={RefreshIcon} size={16} />
                        {syncing ? "Lancement…" : "Lancer une synchronisation"}
                    </Button>
                    {syncStatus && (
                        <p
                            role="status"
                            className={`text-sm ${syncStatus.ok ? "text-up" : "text-destructive"}`}
                        >
                            {syncStatus.message}
                        </p>
                    )}
                </div>
            </div>
        </section>
    );
}
