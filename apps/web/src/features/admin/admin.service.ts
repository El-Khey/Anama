import { request } from "@/lib/http";

/** Points d'entrée HTTP de l'administration (issue ingestion V2). */

/**
 * Réponse (202 Accepted) d'un déclenchement d'ingestion — miroir du DTO back
 * `IngestionJobResponse`. Le travail réel tourne en arrière-plan ; on ne reçoit
 * que la prise en compte.
 */
export interface IngestionJobResponse {
    accepted: boolean;
    source: string;
    target: string;
    message: string;
}

/** Étape d'un import (miroir du back). */
export type IngestionPhase =
    | "fetching-detail"
    | "listing-chapters"
    | "downloading"
    | "finishing";

/** Import en cours — miroir de `IngestionStatusResponse.ActiveImport`. */
export interface ActiveImport {
    slug: string;
    title: string;
    phase: IngestionPhase;
    done: number;
    total: number;
    skipped: number;
    startedAt: string;
}

/** Import en attente — miroir de `IngestionStatusResponse.QueuedImport`. */
export interface QueuedImport {
    slug: string;
    queuedAt: string;
}

/** État courant de l'ingestion — miroir de `IngestionStatusResponse`. */
export interface IngestionStatus {
    active: ActiveImport | null;
    queue: QueuedImport[];
}

/** État live de l'ingestion (import en cours + file). Pour le suivi rafraîchi. */
export function getStatus(): Promise<IngestionStatus> {
    return request<IngestionStatus>("/admin/ingestion/status");
}

/** Importe (ou complète) un roman précis depuis la source. Renvoie 202 + message. */
export function importNovel(slug: string): Promise<IngestionJobResponse> {
    return request<IngestionJobResponse>(
        `/admin/ingestion/novels/${encodeURIComponent(slug)}`,
        { method: "POST" },
    );
}

/** Déclenche un cycle complet de synchronisation (découverte + maintenance). */
export function triggerSync(): Promise<IngestionJobResponse> {
    return request<IngestionJobResponse>("/admin/ingestion/sync", { method: "POST" });
}
