package com.novelrealm.dto.passage;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Pose, remplace ou retire une réaction sur un bloc (#41, §4).
 *
 * <p>Un seul endpoint pour les trois gestes, parce que du point de vue du lecteur
 * c'est un seul geste : toucher un emoji. Toucher celui qu'on a déjà posé le retire,
 * en toucher un autre le remplace — un lecteur n'a qu'une réaction par passage.
 */
public record ReactToPassageRequest(
        @NotNull(message = "Le bloc est obligatoire")
        @Min(value = 0, message = "Index de bloc invalide")
        Integer blockIndex,

        @NotBlank(message = "L'emoji est obligatoire")
        String emoji
) {}
