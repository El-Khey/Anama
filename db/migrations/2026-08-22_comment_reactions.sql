-- =====================================================================
--  Réactions emoji sur les commentaires (façon Discord).
--
--  On peut désormais réagir à un message — de fin de chapitre ou accroché
--  à un passage — avec un ou plusieurs emojis. C'est le pendant, sur les
--  COMMENTAIRES, des réactions qui existaient déjà sur les PASSAGES
--  (passage_annotation.emoji) : ici plusieurs emojis par lecteur sont
--  permis (comme sur Discord), là-bas un seul l'était.
--
--  Table POLYMORPHE, calquée sur comment_mention : un commentaire vit dans
--  deux tables selon son emplacement (chapter_comment, passage_annotation),
--  aucune clé étrangère ne peut couvrir les deux. source_kind + source_id
--  désignent la ligne visée. En contrepartie, ce sont les services qui
--  purgent les réactions quand ils suppriment un commentaire de passage
--  (ceux de chapitre restent : pierre tombale, jamais effacés).
--
--  L'emoji est stocké tel quel (VARCHAR) : aucune liste fermée en base —
--  la barre rapide propose six emojis, mais le bouton « + » ouvre le
--  clavier complet. La validation (c'est bien un emoji, pas du texte
--  arbitraire) est faite côté service, pas par une contrainte SQL.
--
--  À jouer sur une base EXISTANTE :  make migrate
--  (ou : docker compose ... exec -T postgres psql ... < ce_fichier.sql)
--
--  Ré-exécutable sans dommage : IF NOT EXISTS partout. Aucune donnée
--  existante n'est touchée, uniquement de la structure ajoutée.
-- =====================================================================

-- source_kind + source_id : la même paire que comment_mention. Une
-- réaction ne vaut que tant que son commentaire existe, mais faute de FK
-- polymorphe, le nettoyage est à la charge des services (voir plus haut).
-- user_id → users : une réaction disparaît si son auteur disparaît.
-- UNIQUE (source_kind, source_id, user_id, emoji) : plusieurs emojis par
-- lecteur sont permis, mais poser DEUX FOIS le même sur le même message
-- ne compte qu'une fois — c'est aussi ce qui rend le geste idempotent
-- (reposer un emoji déjà là, côté service, le retire).
CREATE TABLE IF NOT EXISTS comment_reaction (
    id           BIGSERIAL PRIMARY KEY,
    source_kind  VARCHAR(20) NOT NULL
                 CHECK (source_kind IN ('CHAPTER_COMMENT', 'PASSAGE_COMMENT')),
    source_id    BIGINT NOT NULL,
    user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    emoji        VARCHAR(16) NOT NULL,
    created_at   TIMESTAMP NOT NULL,
    CONSTRAINT comment_reaction_once_uq
        UNIQUE (source_kind, source_id, user_id, emoji)
);

-- Les réactions de toute une page de fils, en une requête (racines +
-- réponses) : c'est l'accès qui compte, jamais une par message.
CREATE INDEX IF NOT EXISTS idx_comment_reaction_source
    ON comment_reaction (source_kind, source_id);
