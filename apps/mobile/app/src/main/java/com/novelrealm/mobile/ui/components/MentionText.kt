package com.novelrealm.mobile.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.novelrealm.mobile.data.remote.dto.MentionDto

/**
 * Corps d'un commentaire avec ses mentions `@pseudo` mises en évidence et
 * cliquables (issue #45, §2).
 *
 * La recherche se fait sur le **handle** — le pseudo tel qu'il figurait dans le
 * texte à la publication — jamais sur le pseudo actuel : après un renommage,
 * c'est bien l'ancien nom qui est écrit dans le message. L'appui, lui, ouvre le
 * profil par l'identifiant, qui ne change jamais.
 *
 * Un simple `indexOf`, pas d'expression régulière : un pseudo peut contenir
 * n'importe quel caractère, y compris ceux qu'une regex interpréterait.
 */
@Composable
fun MentionText(
    body: String,
    mentions: List<MentionDto>,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    onMentionClick: ((Long) -> Unit)? = null,
) {
    val accent = MaterialTheme.colorScheme.primary
    val annotated = remember(body, mentions, accent, onMentionClick) {
        annotateMentions(body, mentions, accent, onMentionClick)
    }
    Text(
        text = annotated,
        style = style.copy(color = color),
        modifier = modifier,
    )
}

private fun annotateMentions(
    body: String,
    mentions: List<MentionDto>,
    accent: Color,
    onMentionClick: ((Long) -> Unit)?,
): AnnotatedString {
    if (mentions.isEmpty()) return AnnotatedString(body)

    // Toutes les occurrences de chaque « @handle », sans chevauchement — le plus
    // long d'abord, pour que « @Jean-Pierre » ne soit pas mangé par « @Jean ».
    data class Span(val start: Int, val end: Int, val userId: Long)

    val spans = mutableListOf<Span>()
    val taken = mutableListOf<IntRange>()
    mentions
        .filter { it.handle.isNotBlank() }
        .sortedByDescending { it.handle.length }
        .forEach { mention ->
            val needle = "@${mention.handle}"
            var from = 0
            while (true) {
                val at = body.indexOf(needle, from)
                if (at == -1) break
                val range = at until (at + needle.length)
                if (taken.none { it.first < range.last + 1 && range.first < it.last + 1 }) {
                    spans += Span(range.first, range.last + 1, mention.userId)
                    taken += range
                }
                from = at + needle.length
            }
        }

    if (spans.isEmpty()) return AnnotatedString(body)
    spans.sortBy { it.start }

    val mentionStyle = SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)
    return buildAnnotatedString {
        append(body)
        spans.forEach { span ->
            if (onMentionClick != null) {
                addLink(
                    LinkAnnotation.Clickable(
                        tag = "user:${span.userId}",
                        styles = TextLinkStyles(style = mentionStyle),
                        linkInteractionListener = { onMentionClick(span.userId) },
                    ),
                    span.start,
                    span.end,
                )
            } else {
                addStyle(mentionStyle, span.start, span.end)
            }
        }
    }
}
