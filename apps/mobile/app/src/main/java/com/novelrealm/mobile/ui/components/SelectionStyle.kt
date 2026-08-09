package com.novelrealm.mobile.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Habillage d'un élément qu'on choisit : pastille de genre, étagère, option de tri,
 * statut de lecture, réglage du lecteur.
 *
 * <p><b>La règle : l'accent teinte le CONTENU, pas le fond.</b> Un élément retenu se
 * signale par un texte et une icône à la couleur d'accent, sur un voile de cette même
 * couleur à 16 %, cerné d'un filet. Le fond de l'écran reste celui du thème.
 *
 * <p>Le remplissage plein d'accent est réservé à ce qui est vraiment un <b>bouton</b> —
 * la pastille ronde « créer une étagère », le badge de chapitres non lus. Quand on
 * l'appliquait aussi aux sélections, une simple pastille de genre criait plus fort que
 * le bouton d'action à côté d'elle, et une feuille de tri virait au jaune plein.
 *
 * <p>Cet habillage existait déjà dans l'app (`ComposerChip` du lecteur, `SegmentedChoice`
 * des réglages, sélecteur de thème de lecture) : il est ici rassemblé en un seul endroit
 * pour que les écrans cessent de diverger chacun de son côté.
 */
@Immutable
data class SelectionStyle(
    val container: Color,
    val content: Color,
    /** [Color.Transparent] hors sélection : le filet ne doit pas doubler chaque élément. */
    val border: Color,
)

/** Voile d'accent d'un élément retenu. Assez dense pour se voir en thème clair comme sombre. */
private const val SELECTED_CONTAINER_ALPHA = 0.16f

/** Filet de l'élément retenu : c'est lui qui le détache, le voile seul restant discret. */
private const val SELECTED_BORDER_ALPHA = 0.5f

@Composable
fun selectionStyle(selected: Boolean): SelectionStyle = if (selected) {
    SelectionStyle(
        container = MaterialTheme.colorScheme.primary.copy(alpha = SELECTED_CONTAINER_ALPHA),
        content = MaterialTheme.colorScheme.primary,
        border = MaterialTheme.colorScheme.primary.copy(alpha = SELECTED_BORDER_ALPHA),
    )
} else {
    SelectionStyle(
        container = MaterialTheme.colorScheme.surfaceVariant,
        content = MaterialTheme.colorScheme.onSurfaceVariant,
        border = Color.Transparent,
    )
}
