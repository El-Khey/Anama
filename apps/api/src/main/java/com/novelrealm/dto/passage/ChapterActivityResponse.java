package com.novelrealm.dto.passage;

import java.util.List;

/**
 * Toute l'activité d'un chapitre en UN appel (#41, §4).
 *
 * <p>Un appel par bloc serait intenable : un chapitre compte couramment cinquante
 * paragraphes, et le lecteur les fait défiler d'un geste. On envoie donc les
 * compteurs de tous les blocs à l'ouverture, et on ne charge un fil qu'au moment où
 * quelqu'un l'ouvre vraiment.
 *
 * <p>{@code orphanedComments} compte les messages dont l'ancre est morte — le passage
 * visé n'existe plus dans le chapitre. Ils ne sont accrochés nulle part plutôt que
 * d'être posés sur le mauvais paragraphe ; l'app les signale en fin de chapitre.
 */
public record ChapterActivityResponse(
        List<BlockActivityResponse> blocks,
        long orphanedComments
) {}
