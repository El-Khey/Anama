-- =====================================================================
--  Votes (pouce vert / pouce rouge) sur les commentaires.
--
--  On peut désormais approuver (+1) ou désapprouver (-1) un message — de
--  fin de chapitre ou accroché à un passage. Contrairement aux réactions
--  emoji (plusieurs par lecteur, table comment_reaction), un vote est
--  UNIQUE par lecteur et par message : on est pour, ou contre, ou neutre.
--
--  Table POLYMORPHE, calquée sur comment_reaction / comment_mention : un
--  commentaire vit dans deux tables selon son emplacement (chapter_comment,
--  passage_annotation), aucune clé étrangère ne peut couvrir les deux.
--  source_kind + source_id désignent la ligne visée. Ce sont les services
--  qui purgent les votes quand ils suppriment vraiment un commentaire.
--
--  À jouer sur une base EXISTANTE :  make migrate
--  (ou : docker compose ... exec -T postgres psql ... < ce_fichier.sql)
--
--  Ré-exécutable sans dommage : IF NOT EXISTS partout. Aucune donnée
--  existante n'est touchée, uniquement de la structure ajoutée.
-- =====================================================================

-- source_kind + source_id : la même paire que comment_reaction. user_id →
-- users : un vote disparaît si son auteur disparaît. value CHECK (-1, 1) :
-- pas de zéro en base — « neutre » c'est l'absence de ligne, pas un 0.
-- UNIQUE (source_kind, source_id, user_id) : un seul vote par lecteur et
-- par message. Changer d'avis = mettre à jour la ligne (pas en créer une
-- seconde) ; revoter le même sens = supprimer la ligne (retour au neutre).
CREATE TABLE IF NOT EXISTS comment_vote (
    id           BIGSERIAL PRIMARY KEY,
    source_kind  VARCHAR(20) NOT NULL
                 CHECK (source_kind IN ('CHAPTER_COMMENT', 'PASSAGE_COMMENT')),
    source_id    BIGINT NOT NULL,
    user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    value        SMALLINT NOT NULL CHECK (value IN (-1, 1)),
    created_at   TIMESTAMP NOT NULL,
    CONSTRAINT comment_vote_once_uq
        UNIQUE (source_kind, source_id, user_id)
);

-- Les votes de toute une page de fils, en une requête (racines + réponses) :
-- c'est l'accès qui compte, jamais un par message.
CREATE INDEX IF NOT EXISTS idx_comment_vote_source
    ON comment_vote (source_kind, source_id);
