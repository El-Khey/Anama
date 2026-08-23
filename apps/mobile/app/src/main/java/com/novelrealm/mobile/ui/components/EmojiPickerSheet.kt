package com.novelrealm.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Sélecteur d'emoji complet — la feuille qu'ouvre le bouton « + » de la barre de
 * réaction rapide. Recherche en haut, grille par catégories, onglets de catégorie en
 * bas (comme le clavier emoji de Discord).
 *
 * <p>Autonome : l'état vit ici, le seul résultat qui sort est l'emoji remis à [onPick].
 * Se présente au-dessus de tout via un [androidx.compose.ui.window.Dialog] côté appelant.
 */
@Composable
fun EmojiPickerSheet(
    onPick: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableIntStateOf(0) }
    val gridState = rememberLazyGridState()

    val results = remember(query) { EmojiCatalog.search(query) }
    val searching = query.isNotBlank()

    // Fond dédié : en thème sombre un gris neutre un peu plus clair que le fond, pour que
    // la feuille se DÉTACHE nettement (le `surface` par défaut se confond avec la page).
    // En clair on garde `surface`. On tranche sur la luminance — robuste aux thèmes lecteur.
    val surfaceColor = MaterialTheme.colorScheme.surface
    val sheetColor =
        if (surfaceColor.luminance() < 0.5f) Color(0xFF1C1C22) else surfaceColor

    Surface(
        color = sheetColor,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        tonalElevation = 0.dp,
        shadowElevation = 24.dp,
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(horizontal = 18.dp)
                .padding(top = 10.dp, bottom = 14.dp),
        ) {
            // Poignée centrée.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)),
            )

            Spacer(Modifier.height(16.dp))
            // En-tête : titre à gauche, croix ronde discrète à droite.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Réactions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        .clickable(onClick = onClose),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Fermer",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            EmojiSearchField(query = query, onQueryChange = { query = it })

            Spacer(Modifier.height(14.dp))
            if (searching && results.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                ) {
                    Text(
                        text = "Aucun emoji pour « $query »",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // En recherche : une grille plate de résultats. Sinon : la catégorie
                // active, avec son titre en tête (span complet).
                val current = EmojiCatalog.CATEGORIES[category]
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(7),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                ) {
                    if (!searching) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = current.label.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                            )
                        }
                    }
                    val shown = if (searching) results else current.emojis
                    items(shown, key = { it }) { emoji ->
                        EmojiCell(emoji = emoji, onClick = { onPick(emoji) })
                    }
                }
            }

            // Onglets de catégorie, masqués pendant une recherche (les résultats
            // traversent déjà toutes les catégories).
            if (!searching) {
                Spacer(Modifier.height(10.dp))
                // Filet fin au-dessus des onglets, pour les séparer de la grille.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
                )
                Spacer(Modifier.height(8.dp))
                CategoryTabs(
                    selected = category,
                    onSelect = { category = it },
                )
            }
        }
    }
}

/**
 * Le champ de recherche d'emoji. Il ne prend PLUS le clavier tout seul à l'ouverture :
 * on ouvre ce sélecteur pour RÉAGIR (choisir un emoji d'un tap), pas pour taper — un
 * clavier qui surgit alors recouvrait la moitié des emojis et donnait l'impression
 * qu'une réaction « ouvre le clavier ». Le champ prend le focus quand on le touche.
 */
@Composable
private fun EmojiSearchField(query: String, onQueryChange: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(horizontal = 13.dp, vertical = 10.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(9.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = "Rechercher…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    .clickable { onQueryChange("") },
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Effacer",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

/** Une case d'emoji dans la grille — carrée, l'emoji centré, léger fond au survol tactile. */
@Composable
private fun EmojiCell(emoji: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        Text(text = emoji, fontSize = 25.sp)
    }
}

/**
 * La barre d'onglets de catégorie, en bas (schéma clavier emoji). Le premier emoji de
 * chaque catégorie sert d'icône d'onglet — pas de libellé, la place manque. L'onglet
 * actif porte une pastille pleine (accent) : la sélection se voit d'un coup d'œil.
 */
@Composable
private fun CategoryTabs(selected: Int, onSelect: (Int) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        EmojiCatalog.CATEGORIES.forEachIndexed { index, category ->
            val active = index == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) accent.copy(alpha = 0.16f)
                        else Color.Transparent,
                    )
                    .clickable { onSelect(index) },
            ) {
                Text(
                    text = category.emojis.first(),
                    fontSize = if (active) 21.sp else 18.sp,
                )
            }
        }
    }
}
