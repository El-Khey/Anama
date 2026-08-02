package com.novelrealm.mobile.ui.util

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Formatage des dates ISO renvoyées par le back (Instant sérialisé en texte).

private val dayFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH)
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH)

fun parseInstant(iso: String?): Instant? {
    if (iso.isNullOrBlank()) return null
    return runCatching { Instant.parse(iso) }
        .getOrElse { runCatching { OffsetDateTime.parse(iso).toInstant() }.getOrNull() }
}

// « Aujourd'hui » / « Hier » / « 12 mars 2026 » — pour grouper l'historique.
fun relativeDayLabel(iso: String?): String {
    val instant = parseInstant(iso) ?: return "Plus tôt"
    val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when (date) {
        today -> "Aujourd'hui"
        today.minusDays(1) -> "Hier"
        else -> date.format(dayFormatter)
    }
}

fun timeLabel(iso: String?): String {
    val instant = parseInstant(iso) ?: return ""
    return instant.atZone(ZoneId.systemDefault()).toLocalTime().format(timeFormatter)
}

fun dateLabel(iso: String?): String {
    val instant = parseInstant(iso) ?: return ""
    return instant.atZone(ZoneId.systemDefault()).toLocalDate().format(dayFormatter)
}

/**
 * « à l'instant » / « il y a 12 min » / « il y a 3 h » / « il y a 4 j », puis la date
 * complète au-delà d'une semaine — dans un fil de discussion, l'ancienneté relative
 * dit bien plus que la date exacte.
 *
 * Une horloge locale en avance sur celle du serveur donnerait un écart négatif : il
 * retombe sur « à l'instant » plutôt que sur un « il y a -3 min ».
 */
fun relativeTimeLabel(iso: String?): String {
    val instant = parseInstant(iso) ?: return ""
    val minutes = Duration.between(instant, Instant.now()).toMinutes()
    return when {
        minutes < 1 -> "à l'instant"
        minutes < 60 -> "il y a $minutes min"
        minutes < 24 * 60 -> "il y a ${minutes / 60} h"
        minutes < 7 * 24 * 60 -> "il y a ${minutes / (24 * 60)} j"
        else -> dateLabel(iso)
    }
}
