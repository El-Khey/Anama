-- =====================================================================
--  Commentaires de fin de chapitre (issue #41).
--
--  POURQUOI CE FICHIER : `db/init/01_init.sql` n'est joué qu'UNE fois, à
--  la création du volume Postgres. Une base déjà remplie ne le rejouera
--  jamais — il faut donc appliquer le changement à la main.
--
--  À jouer sur une base EXISTANTE :
--      docker compose -p novelrealm -f docker-compose.yml -f docker-compose.dev.yml \
--          exec -T postgres psql -U novelrealm -d novelrealm \
--          < db/migrations/2026-08-02_chapter_comment.sql
--
--  Sur une base neuve (`make clean` puis `make dev`), ne rien faire :
--  01_init.sql contient déjà ce schéma. Le script est de toute façon
--  ré-exécutable sans dommage (IF NOT EXISTS partout).
-- =====================================================================

CREATE TABLE IF NOT EXISTS chapter_comment (
    id          BIGSERIAL PRIMARY KEY,
    chapter_id  BIGINT NOT NULL REFERENCES chapters(id)     ON DELETE CASCADE,
    user_id     BIGINT NOT NULL REFERENCES users(id)        ON DELETE CASCADE,
    parent_id   BIGINT          REFERENCES chapter_comment(id) ON DELETE CASCADE,
    body        TEXT NOT NULL,
    is_spoiler  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP NOT NULL,
    deleted_at  TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chapter_comment_chapter_created
    ON chapter_comment (chapter_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_chapter_comment_parent
    ON chapter_comment (parent_id);

CREATE INDEX IF NOT EXISTS idx_chapter_comment_user_created
    ON chapter_comment (user_id, created_at DESC);
