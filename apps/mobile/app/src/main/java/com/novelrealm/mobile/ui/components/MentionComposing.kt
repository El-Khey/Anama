package com.novelrealm.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.novelrealm.mobile.data.remote.dto.UserSearchDto
import com.novelrealm.mobile.data.remote.resolveImageUrl

/**
 * Autocomplétion des mentions (issue #45, §2) — la partie « texte » : détecter le
 * `@…` en cours de frappe, et l'y remplacer par le pseudo choisi.
 *
 * Les deux fonctions sont pures et partagées par les deux composers (fin de
 * chapitre, passage) : la règle de détection DOIT être la même partout, sinon le
 * même geste se comporte différemment selon l'écran.
 */

/** Longueur maximale du jeton cherché — au-delà, ce n'est plus un pseudo qu'on tape. */
private const val MAX_QUERY_LENGTH = 30

/**
 * Le jeton `@…` en cours de frappe **en fin de brouillon**, ou `null`.
 *
 * Un `@` ne déclenche la recherche que s'il ouvre un mot (début de texte ou
 * précédé d'un espace) : « email@exemple.fr » ne doit pas ouvrir de suggestions.
 * Le jeton peut contenir des espaces — les pseudos en contiennent — mais pas de
 * retour à la ligne : aller à la ligne, c'est visiblement passer à autre chose.
 */
fun activeMentionQuery(draft: String): String? {
    val at = draft.lastIndexOf('@')
    if (at == -1) return null
    if (at > 0 && !draft[at - 1].isWhitespace()) return null
    val token = draft.substring(at + 1)
    if (token.length > MAX_QUERY_LENGTH || token.contains('\n')) return null
    return token
}

/** Remplace le jeton `@…` final par « @pseudo » suivi d'une espace, prêt à continuer. */
fun applyMention(draft: String, pseudo: String): String {
    val at = draft.lastIndexOf('@')
    if (at == -1) return draft
    return draft.substring(0, at) + "@" + pseudo + " "
}

/**
 * La rangée de suggestions, posée juste au-dessus du champ de saisie — des
 * pastilles horizontales plutôt qu'une liste verticale : elle ne recouvre ainsi
 * jamais la discussion qu'on est en train de lire.
 */
@Composable
fun MentionSuggestionRow(
    suggestions: List<UserSearchDto>,
    onPick: (UserSearchDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (suggestions.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 8.dp),
    ) {
        suggestions.forEach { user ->
            SuggestionChip(user = user, onClick = { onPick(user) })
            Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
private fun SuggestionChip(user: UserSearchDto, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        shape = RoundedCornerShape(50),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onClick)
                .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        ) {
            val avatar = resolveImageUrl(user.avatarUrl)
            if (avatar != null) {
                AsyncImage(
                    model = avatar,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(22.dp).clip(CircleShape),
                )
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                ) {
                    Text(
                        text = user.pseudo.take(1).uppercase().ifBlank { "?" },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = user.pseudo,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
