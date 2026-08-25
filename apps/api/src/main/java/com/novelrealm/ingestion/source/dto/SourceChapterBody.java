package com.novelrealm.ingestion.source.dto;

import java.math.BigDecimal;

/**
 * Le CORPS d'un chapitre : le texte lui-même, plus le strict nécessaire pour
 * décider s'il faut le persister.
 *
 * @param number numéro RÉEL chez la source (float)
 * @param title  titre du chapitre
 * @param body   texte du chapitre (peut être vide si verrouillé/retenu)
 * @param locked chapitre premium / fenêtre temporelle : corps retenu → non persisté
 */
public record SourceChapterBody(BigDecimal number, String title, String body, boolean locked) {

    /** Rien d'exploitable à enregistrer : verrouillé, ou corps vide. */
    public boolean isEmptyOrLocked() {
        return locked || body == null || body.isBlank();
    }
}
