-- =====================================================================
--  Correctif #2 (issue #45) : mentions, notifications, GIF.
--
--  Trois ajouts, AUCUNE donnée existante touchée :
--    1. comment_mention   — les @pseudo résolus des commentaires (§2)
--    2. notification      — la cloche : réponses et mentions (§3)
--    3. gif_url / gif_preview_url — GIF joint aux commentaires (§5)
--
--  À jouer sur une base EXISTANTE :
--      make migrate
--
--  Ré-exécutable sans dommage : tout est IF NOT EXISTS.
--
--  Le MÊME schéma est décrit dans db/init/01_init.sql pour les bases
--  neuves — « make check-schema » compare les deux chemins.
--
--  Ordre de déploiement : cette migration AVANT le nouveau code
--  (make migrate puis make prod). Le DDL est purement additif, l'ancien
--  code tourne dessus sans le voir ; l'inverse ferait échouer chaque
--  réponse à un commentaire (INSERT dans une table absente) le temps de
--  la fenêtre.
-- =====================================================================

-- ──────────────── Mentions @pseudo (issue #45, §2) ──────────────────
-- Résolues à la PUBLICATION : le texte garde « @pseudo » tel que tapé,
-- cette table fige QUI était visé (l'id) et SOUS QUEL NOM (handle).
-- Un renommage ultérieur ne casse donc ni le lien ni la mise en évidence.
--
-- source_kind + source_id : un commentaire vit dans deux tables selon son
-- emplacement (chapter_comment, passage_annotation) — pas de FK possible.
-- En contrepartie, PassageSocialService purge les mentions quand il
-- supprime un commentaire de passage (ceux de chapitre restent : pierre
-- tombale, jamais effacés).
CREATE TABLE IF NOT EXISTS comment_mention (
    id                 BIGSERIAL PRIMARY KEY,
    source_kind        VARCHAR(20) NOT NULL
                       CHECK (source_kind IN ('CHAPTER_COMMENT', 'PASSAGE_COMMENT')),
    source_id          BIGINT NOT NULL,
    mentioned_user_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    handle             VARCHAR(255) NOT NULL,               -- pseudo tel que tapé
    created_at         TIMESTAMP NOT NULL,
    -- Mentionner deux fois la même personne dans un message = une mention.
    CONSTRAINT comment_mention_once_uq UNIQUE (source_kind, source_id, mentioned_user_id)
);

-- Les mentions d'une page de fils, en une requête.
CREATE INDEX IF NOT EXISTS idx_comment_mention_source
    ON comment_mention (source_kind, source_id);

-- ──────────────── Notifications (issue #45, §3) ──────────────────────
-- La cloche : réponses à mes commentaires, mentions. Le CHECK réserve
-- NEW_CHAPTER pour l'issue #22, qui se greffera ici sans migration.
--
-- L'extrait est FIGÉ à la création : une notification raconte ce qui
-- s'est passé, elle survit à la suppression du commentaire d'origine.
-- comment_id est sans FK (table polymorphe) ; actor/novel/chapter sont
-- nullables ET en cascade : un compte ou un roman supprimé emporte ses
-- notifications, qui n'auraient plus rien à ouvrir.
CREATE TABLE IF NOT EXISTS notification (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type          VARCHAR(30) NOT NULL
                  CHECK (type IN ('COMMENT_REPLY', 'MENTION', 'NEW_CHAPTER')),
    actor_id      BIGINT REFERENCES users(id)    ON DELETE CASCADE,
    novel_id      BIGINT REFERENCES novels(id)   ON DELETE CASCADE,
    chapter_id    BIGINT REFERENCES chapters(id) ON DELETE CASCADE,
    comment_kind  VARCHAR(20)
                  CHECK (comment_kind IN ('CHAPTER_COMMENT', 'PASSAGE_COMMENT')),
    comment_id    BIGINT,
    block_index   INT,                                     -- passages : index à l'événement
    excerpt       VARCHAR(200),
    is_read       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP NOT NULL
);

-- Le badge : COUNT des non-lues, à chaque retour de l'app au premier plan.
CREATE INDEX IF NOT EXISTS idx_notification_user_read
    ON notification (user_id, is_read);

-- La liste : les miennes, les plus récentes d'abord.
CREATE INDEX IF NOT EXISTS idx_notification_user_created
    ON notification (user_id, created_at DESC);

-- ──────────────── GIF joints (issue #45, §5) ─────────────────────────
-- Deux URL Tenor par message (l'animée + l'image figée affichée par
-- défaut), jamais un fichier chez nous. NULL = message texte ordinaire.
ALTER TABLE chapter_comment    ADD COLUMN IF NOT EXISTS gif_url         VARCHAR(500);
ALTER TABLE chapter_comment    ADD COLUMN IF NOT EXISTS gif_preview_url VARCHAR(500);
ALTER TABLE passage_annotation ADD COLUMN IF NOT EXISTS gif_url         VARCHAR(500);
ALTER TABLE passage_annotation ADD COLUMN IF NOT EXISTS gif_preview_url VARCHAR(500);
