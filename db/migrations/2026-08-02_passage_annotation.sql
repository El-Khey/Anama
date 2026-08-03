-- =====================================================================
--  Annotations de passage : citations, et socle des réactions (issue #41).
--
--  POURQUOI CE FICHIER : `db/init/01_init.sql` n'est joué qu'UNE fois, à
--  la création du volume Postgres. Une base déjà remplie ne le rejouera
--  jamais — il faut donc appliquer le changement à la main.
--
--  À jouer sur une base EXISTANTE :
--      docker compose -p novelrealm -f docker-compose.yml -f docker-compose.dev.yml \
--          exec -T postgres psql -U novelrealm -d novelrealm \
--          < db/migrations/2026-08-02_passage_annotation.sql
--
--  Sur une base neuve (`make clean` puis `make dev`), ne rien faire :
--  01_init.sql contient déjà ce schéma. Le script est de toute façon
--  ré-exécutable sans dommage (IF NOT EXISTS partout).
-- =====================================================================

-- ──────────────────── Annotations de passage (#41) ──────────────────
-- Tout ce qui s'accroche à un PASSAGE d'un chapitre : citation, réaction,
-- surlignage, commentaire de passage. Une seule table car tous partagent
-- exactement la même chose — l'ancre. Seul `kind = 'QUOTE'` est produit
-- aujourd'hui ; les autres valeurs sont posées d'avance pour ne pas avoir
-- à migrer quand elles arriveront.
--
-- L'ANCRE = block_index + text_hash. L'index seul se décale dès qu'un
-- chapitre est ré-ingéré avec un bloc de plus ; l'empreinte permet de
-- retrouver le bloc ailleurs, ou de constater qu'il a disparu.
--
-- quoted_text est FIGÉ à la capture : la collection de citations doit
-- rester lisible même si le chapitre change ou disparaît. L'ancre n'est
-- qu'un bonus, elle sert le bouton « aller au passage ».
CREATE TABLE IF NOT EXISTS passage_annotation (
    id            BIGSERIAL PRIMARY KEY,
    chapter_id    BIGINT NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,
    user_id       BIGINT NOT NULL REFERENCES users(id)    ON DELETE CASCADE,
    block_index   INT NOT NULL,                            -- position du bloc
    text_hash     VARCHAR(32) NOT NULL,                    -- empreinte du bloc
    start_offset  INT NOT NULL,                            -- début dans le bloc (inclus)
    end_offset    INT NOT NULL,                            -- fin dans le bloc (exclu)
    kind          VARCHAR(20) NOT NULL
                  CHECK (kind IN ('REACTION', 'COMMENT', 'HIGHLIGHT', 'QUOTE')),
    quoted_text   TEXT,                                    -- figé à la capture
    emoji         VARCHAR(16),                             -- réservé aux réactions
    body          TEXT,                                    -- réservé aux commentaires/notes
    is_private    BOOLEAN NOT NULL DEFAULT TRUE,
    is_spoiler    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP NOT NULL
);

-- Page « Mes citations » : les miennes, les plus récentes d'abord.
CREATE INDEX IF NOT EXISTS idx_passage_annotation_user_kind_created
    ON passage_annotation (user_id, kind, created_at DESC);

-- Agrégats par chapitre (réactions à venir) et résolution d'ancre.
CREATE INDEX IF NOT EXISTS idx_passage_annotation_chapter_block
    ON passage_annotation (chapter_id, block_index);
