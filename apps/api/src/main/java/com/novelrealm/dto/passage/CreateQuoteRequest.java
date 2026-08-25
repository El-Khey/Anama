package com.novelrealm.dto.passage;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Création d'une citation. Le client envoie des <b>coordonnées</b>, jamais le texte.
 *
 * <p>C'est le serveur qui extrait le passage du chapitre, calcule l'empreinte du bloc
 * et fige le texte. Ainsi le texte cité correspond forcément au chapitre — un client
 * ne peut pas ranger dans sa collection une phrase qui n'y a jamais été — et
 * l'empreinte est calculée en un seul endroit, donc jamais en désaccord.
 */
public record CreateQuoteRequest(
        @NotNull(message = "Le bloc est obligatoire")
        @Min(value = 0, message = "Index de bloc invalide")
        Integer blockIndex,

        @NotNull(message = "Le début de la sélection est obligatoire")
        @Min(value = 0, message = "Début de sélection invalide")
        Integer startOffset,

        @NotNull(message = "La fin de la sélection est obligatoire")
        @Min(value = 1, message = "Fin de sélection invalide")
        Integer endOffset
) {}
