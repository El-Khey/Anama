/** Utilisateur authentifié, tel que renvoyé par l'API (profil enrichi — issue #17). */
export interface User {
    id: number;
    pseudo: string;
    email: string;
    bio: string | null;
    /** Chemin local (/uploads/…) ou URL externe (photo Google). */
    avatarUrl: string | null;
    bannerUrl: string | null;
    provider: "LOCAL" | "GOOGLE";
    /** Préférences JSON opaques (accent, réglages du lecteur…) — interprétées par features/profile. */
    preferences: unknown;
    createdAt: string;
    /**
     * L'utilisateur fait-il partie de l'allow-list admin (back : `app.admin-emails`) ?
     * Renseigné uniquement dans la réponse « own » (`/users/me`). Pilote l'affichage
     * du lien Admin et l'accès à la page /admin. Optionnel par prudence (anciens
     * caches / réponses publiques ne le portent pas) → traité comme `false`.
     */
    admin?: boolean;
}
