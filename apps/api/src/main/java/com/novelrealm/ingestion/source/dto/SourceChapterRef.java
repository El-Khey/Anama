package com.novelrealm.ingestion.source.dto;

import java.math.BigDecimal;

/**
 * Référence LÉGÈRE d'un chapitre (sans le corps) telle que listée par la
 * source. Sert à énumérer/paginer les chapitres avant de décider lesquels
 * télécharger.
 *
 * @param number numéro RÉEL chez la source (float, ex. 1.5)
 * @param title  titre du chapitre (peut être vide)
 */
public record SourceChapterRef(BigDecimal number, String title) {
}
