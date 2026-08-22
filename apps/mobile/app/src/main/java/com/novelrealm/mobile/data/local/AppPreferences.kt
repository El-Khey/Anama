package com.novelrealm.mobile.data.local

// Modèle des préférences utilisateur (thème de l'app + confort de lecture).
//
// Les identifiants (`id`) sont volontairement IDENTIQUES à ceux du front web
// (`apps/web/src/features/profile/preferences.ts`) : les réglages sont stockés dans le
// champ `preferences` du compte, donc web et mobile doivent parler la même langue pour
// qu'un changement d'un côté se retrouve de l'autre.

/** Clair / sombre / suit le réglage du téléphone. */
enum class ThemeMode(val id: String, val label: String) {
    SYSTEM("system", "Système"),
    LIGHT("light", "Clair"),
    DARK("dark", "Sombre");

    companion object {
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/** Couleur d'accent de l'app — mêmes 6 teintes que le web. */
enum class AccentId(val id: String, val label: String) {
    CRIMSON("crimson", "Cramoisi"),
    EMBER("ember", "Braise"),
    AMBER("amber", "Ambre"),
    EMERALD("emerald", "Émeraude"),
    AZURE("azure", "Azur"),
    VIOLET("violet", "Violet");

    companion object {
        val DEFAULT = CRIMSON
        fun fromId(id: String?): AccentId = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** Ambiance du lecteur (fond + couleur du texte), indépendante du thème de l'app. */
enum class ReaderTheme(val id: String, val label: String) {
    LIGHT("clair", "Clair"),
    SEPIA("sepia", "Sépia"),
    DARK("sombre", "Sombre"),
    OLED("oled", "Noir profond");

    companion object {
        fun fromId(id: String?): ReaderTheme = entries.firstOrNull { it.id == id } ?: LIGHT
    }
}

/** Famille typographique du lecteur. */
enum class ReaderFont(val id: String, val label: String) {
    SANS("sans", "Sans serif"),
    SERIF("serif", "Serif"),
    MONO("mono", "Monospace");

    companion object {
        fun fromId(id: String?): ReaderFont = entries.firstOrNull { it.id == id } ?: SANS

        /** `true` si l'identifiant correspond à une police que le mobile sait rendre. */
        fun isKnown(id: String?): Boolean = entries.any { it.id == id }
    }
}

/**
 * Réglages de confort de lecture. Les bornes correspondent à celles du web pour que
 * les valeurs restent valides des deux côtés.
 */
data class ReaderPrefs(
    val fontSize: Int = 17,             // 14..30 sp
    val lineHeight: Float = 1.6f,       // 1.3..2.4 (multiplicateur)
    val paragraphGap: Float = 1.0f,     // 0.4..2.4 (en « em »)
    val font: ReaderFont = ReaderFont.SANS,
    val justify: Boolean = false,
    val theme: ReaderTheme = ReaderTheme.LIGHT,
    val keepScreenOn: Boolean = true,   // spécifique mobile
    val fullscreen: Boolean = false,    // spécifique mobile (masque les barres système)
    /**
     * Marge latérale du texte, en dp (0 = le texte occupe toute la largeur).
     *
     * Clé propre au mobile : le web règle la largeur de ligne par paliers nommés
     * (`widthId`), notion qui n'a pas de sens sur un téléphone où l'écran est déjà
     * étroit. On ne touche donc pas à `widthId`, qui reste préservé tel quel.
     */
    val margin: Int = 20,               // 0..40 dp
    /**
     * Affiche (ou non) la couche **commentaires dans le texte** : les marques en marge
     * des paragraphes commentés, les fils qu'elles ouvrent, et l'action « Commenter »
     * de la barre de passage.
     *
     * Réglage de compte, pas d'appareil : il part dans `preferences` comme les autres,
     * donc le couper sur le téléphone le coupe partout.
     *
     * Les **réactions ont leur propre réglage** ([inTextReactions]) : on peut vouloir
     * les puces de réaction sous les paragraphes sans les fils de commentaires, ou
     * l'inverse. Les citations, elles, ne sont concernées par aucun des deux — citer
     * est un geste solitaire, pas social.
     */
    val inTextComments: Boolean = true,
    /**
     * Affiche (ou non) les **réactions emoji sous les paragraphes** : les petites puces
     * façon Discord, et la barre de réaction rapide qu'ouvre le double tap.
     *
     * Réglage de compte comme [inTextComments], et indépendant de lui : couper les
     * commentaires ne coupe pas les réactions, et réciproquement.
     */
    val inTextReactions: Boolean = true,
) {
    companion object {
        val DEFAULT = ReaderPrefs()
        val FONT_SIZE_RANGE = 14f..30f
        val LINE_HEIGHT_RANGE = 1.3f..2.4f
        val PARAGRAPH_GAP_RANGE = 0.4f..2.4f
        val MARGIN_RANGE = 0f..40f
    }
}

/** Toutes les préférences de l'app réunies. */
data class AppPrefs(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentId = AccentId.DEFAULT,
    val reader: ReaderPrefs = ReaderPrefs.DEFAULT,
) {
    companion object {
        val DEFAULT = AppPrefs()
    }
}
