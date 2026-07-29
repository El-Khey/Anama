package com.novelrealm.mobile.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Préférences de l'app (thème + lecture), avec **deux niveaux** :
 *
 * 1. **Local** (SharedPreferences) — source d'affichage immédiate : le thème s'applique
 *    sans attendre le réseau et survit hors ligne. Rien de sensible ici, donc pas besoin
 *    du stockage chiffré réservé au token ([TokenStorage]).
 * 2. **Compte** (champ `preferences` du back) — pour retrouver ses réglages sur le web
 *    et sur un autre appareil. L'envoi est **débouncé** et « best-effort » : un échec
 *    réseau ne doit jamais empêcher un réglage de s'appliquer localement.
 *
 * ⚠️ Le back **remplace le champ `preferences` en bloc**. On garde donc le dernier objet
 * reçu du serveur et on ne réécrit que nos clés (fusion) : les réglages propres au web
 * (`fg`, `bg`, `widthId`, `focusMode`…) sont préservés au lieu d'être écrasés.
 */
class PreferencesStore(
    context: Context,
    private val scope: CoroutineScope,
    /** Pousse l'objet `preferences` complet vers le back. Branché par le ServiceLocator. */
    private val pushToServer: suspend (JsonObject) -> Unit,
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _prefs = MutableStateFlow(readLocal())
    val state: StateFlow<AppPrefs> = _prefs.asStateFlow()

    /** Dernier objet `preferences` connu du serveur, pour une fusion non destructive. */
    private var lastRemote: JsonObject = JsonObject(emptyMap())
    private var syncJob: Job? = null

    /**
     * Police choisie sur le web que le mobile ne sait pas rendre (le web propose par
     * exemple une police « dyslexique »). On l'affiche avec notre police par défaut,
     * mais on la **réécrit telle quelle** lors de la synchronisation : sans ça, ouvrir
     * l'app mobile détruirait définitivement ce choix côté web. Effacée dès que
     * l'utilisateur change lui-même de police sur le téléphone.
     */
    private var unsupportedRemoteFontId: String? = null

    // ── Mutations ────────────────────────────────────────────────────────────

    fun setThemeMode(mode: ThemeMode) = update { it.copy(themeMode = mode) }

    fun setAccent(accent: AccentId) = update { it.copy(accent = accent) }

    fun updateReader(transform: (ReaderPrefs) -> ReaderPrefs) =
        update { it.copy(reader = transform(it.reader)) }

    /** Remet les réglages de lecture à leurs valeurs d'origine (thème de l'app conservé). */
    fun resetReader() = update { it.copy(reader = ReaderPrefs.DEFAULT) }

    /** Remet TOUTES les préférences à zéro (thème compris). */
    fun resetAll() = update { AppPrefs.DEFAULT }

    private fun update(transform: (AppPrefs) -> AppPrefs) {
        val previous = _prefs.value
        val next = transform(previous)
        if (next == previous) return
        // Choix explicite de l'utilisateur sur mobile → il remplace la police du web.
        if (next.reader.font != previous.reader.font) unsupportedRemoteFontId = null
        _prefs.value = next
        writeLocal(next)
        scheduleSync()
    }

    // ── Synchronisation compte ───────────────────────────────────────────────

    /**
     * Applique les préférences venues du compte (appelé après un `GET /users/me`).
     * Le local est écrasé par le distant : c'est le compte qui fait foi au chargement.
     */
    fun hydrateFromRemote(remote: JsonElement?) {
        val obj = remote as? JsonObject ?: return
        lastRemote = obj

        // Chaque clé ABSENTE laisse la valeur locale intacte : le web n'écrit que
        // `accent` et `reader`, il ne doit pas remettre à zéro un mode d'affichage
        // choisi sur le téléphone.
        val current = _prefs.value
        val readerObj = obj["reader"] as? JsonObject

        // Police du web non gérée ici : on la mémorise pour la restituer intacte.
        val remoteFontId = readerObj?.str("fontId")
        unsupportedRemoteFontId =
            if (remoteFontId != null && !ReaderFont.isKnown(remoteFontId)) remoteFontId else null
        val merged = AppPrefs(
            themeMode = obj.str("themeMode")?.let { ThemeMode.fromId(it) } ?: current.themeMode,
            accent = obj.str("accent")?.let { AccentId.fromId(it) } ?: current.accent,
            reader = readerObj?.toReaderPrefs(current.reader) ?: current.reader,
        )
        if (merged == _prefs.value) return
        _prefs.value = merged
        writeLocal(merged)
    }

    /** Oublie l'état de synchronisation (déconnexion) — les réglages locaux restent. */
    fun onSessionEnded() {
        syncJob?.cancel()
        lastRemote = JsonObject(emptyMap())
        unsupportedRemoteFontId = null
    }

    // Envoi débouncé : on laisse retomber les rafales (glissement d'un slider) avant
    // d'écrire sur le réseau.
    private fun scheduleSync() {
        syncJob?.cancel()
        syncJob = scope.launch {
            delay(SYNC_DEBOUNCE_MS)
            val payload = buildRemotePayload(_prefs.value)
            runCatching { pushToServer(payload) }   // best-effort : un échec est sans conséquence
                .onSuccess { lastRemote = payload }
        }
    }

    // Fusion non destructive : on repart de l'objet distant et on ne réécrit que nos clés.
    private fun buildRemotePayload(current: AppPrefs): JsonObject {
        val previousReader = (lastRemote["reader"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        previousReader["themeId"] = JsonPrimitive(current.reader.theme.id)
        previousReader["fontId"] = JsonPrimitive(unsupportedRemoteFontId ?: current.reader.font.id)
        previousReader["fontSize"] = JsonPrimitive(current.reader.fontSize)
        previousReader["lineHeight"] = JsonPrimitive(current.reader.lineHeight)
        previousReader["paragraphGap"] = JsonPrimitive(current.reader.paragraphGap)
        previousReader["justify"] = JsonPrimitive(current.reader.justify)
        // Clés propres au mobile — ignorées par le web, qui ne lit que les siennes.
        previousReader["keepScreenOn"] = JsonPrimitive(current.reader.keepScreenOn)
        previousReader["fullscreen"] = JsonPrimitive(current.reader.fullscreen)

        val root = lastRemote.toMutableMap()
        root["accent"] = JsonPrimitive(current.accent.id)
        root["themeMode"] = JsonPrimitive(current.themeMode.id)
        root["reader"] = JsonObject(previousReader)
        return JsonObject(root)
    }

    // ── Persistance locale ───────────────────────────────────────────────────

    private fun readLocal(): AppPrefs {
        val d = ReaderPrefs.DEFAULT
        return AppPrefs(
            themeMode = ThemeMode.fromId(prefs.getString(KEY_THEME_MODE, null)),
            accent = AccentId.fromId(prefs.getString(KEY_ACCENT, null)),
            reader = ReaderPrefs(
                fontSize = prefs.getInt(KEY_FONT_SIZE, d.fontSize),
                lineHeight = prefs.getFloat(KEY_LINE_HEIGHT, d.lineHeight),
                paragraphGap = prefs.getFloat(KEY_PARAGRAPH_GAP, d.paragraphGap),
                font = ReaderFont.fromId(prefs.getString(KEY_FONT, null)),
                justify = prefs.getBoolean(KEY_JUSTIFY, d.justify),
                theme = ReaderTheme.fromId(prefs.getString(KEY_READER_THEME, null)),
                keepScreenOn = prefs.getBoolean(KEY_KEEP_SCREEN_ON, d.keepScreenOn),
                fullscreen = prefs.getBoolean(KEY_FULLSCREEN, d.fullscreen),
            ),
        )
    }

    private fun writeLocal(value: AppPrefs) {
        prefs.edit()
            .putString(KEY_THEME_MODE, value.themeMode.id)
            .putString(KEY_ACCENT, value.accent.id)
            .putInt(KEY_FONT_SIZE, value.reader.fontSize)
            .putFloat(KEY_LINE_HEIGHT, value.reader.lineHeight)
            .putFloat(KEY_PARAGRAPH_GAP, value.reader.paragraphGap)
            .putString(KEY_FONT, value.reader.font.id)
            .putBoolean(KEY_JUSTIFY, value.reader.justify)
            .putString(KEY_READER_THEME, value.reader.theme.id)
            .putBoolean(KEY_KEEP_SCREEN_ON, value.reader.keepScreenOn)
            .putBoolean(KEY_FULLSCREEN, value.reader.fullscreen)
            .apply()
    }

    private companion object {
        const val FILE_NAME = "novelrealm_prefs"
        const val SYNC_DEBOUNCE_MS = 1200L

        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_ACCENT = "accent"
        const val KEY_FONT_SIZE = "reader_font_size"
        const val KEY_LINE_HEIGHT = "reader_line_height"
        const val KEY_PARAGRAPH_GAP = "reader_paragraph_gap"
        const val KEY_FONT = "reader_font"
        const val KEY_JUSTIFY = "reader_justify"
        const val KEY_READER_THEME = "reader_theme"
        const val KEY_KEEP_SCREEN_ON = "reader_keep_screen_on"
        const val KEY_FULLSCREEN = "reader_fullscreen"
    }
}

// ── Lecture tolérante du JSON distant (une clé absente ou d'un type inattendu
// laisse simplement la valeur courante inchangée). ──────────────────────────────

private fun JsonObject.str(key: String): String? =
    runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()

private fun JsonObject.int(key: String): Int? =
    runCatching { this[key]?.jsonPrimitive?.intOrNull }.getOrNull()

private fun JsonObject.float(key: String): Float? =
    runCatching { this[key]?.jsonPrimitive?.floatOrNull }.getOrNull()

private fun JsonObject.bool(key: String): Boolean? =
    runCatching { this[key]?.jsonPrimitive?.booleanOrNull }.getOrNull()

private fun JsonObject.toReaderPrefs(current: ReaderPrefs): ReaderPrefs = ReaderPrefs(
    fontSize = int("fontSize")?.coerceIn(14, 30) ?: current.fontSize,
    lineHeight = float("lineHeight")?.coerceIn(1.3f, 2.4f) ?: current.lineHeight,
    paragraphGap = float("paragraphGap")?.coerceIn(0.4f, 2.4f) ?: current.paragraphGap,
    font = str("fontId")?.let { ReaderFont.fromId(it) } ?: current.font,
    justify = bool("justify") ?: current.justify,
    theme = str("themeId")?.let { ReaderTheme.fromId(it) } ?: current.theme,
    keepScreenOn = bool("keepScreenOn") ?: current.keepScreenOn,
    fullscreen = bool("fullscreen") ?: current.fullscreen,
)
