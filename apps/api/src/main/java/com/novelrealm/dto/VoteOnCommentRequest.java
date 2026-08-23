package com.novelrealm.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Vote (pouce vert / pouce rouge) sur un commentaire.
 *
 * <p>{@code value} vaut {@code +1} (pour) ou {@code -1} (contre). Un seul endpoint, un
 * seul geste : toucher un pouce. Revoter le même sens retire son vote (retour au
 * neutre), voter l'autre sens le bascule — c'est le service qui tranche selon l'état
 * courant. Le neutre ne s'envoie donc pas : il est le résultat, pas une valeur reçue.
 */
public record VoteOnCommentRequest(
        @NotNull(message = "Le sens du vote est obligatoire")
        Integer value
) {}
