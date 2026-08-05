-- =====================================================================
--  Réponses aux commentaires de passage (issue #41, §4).
--
--  À jouer sur une base EXISTANTE :
--      docker compose -p novelrealm -f docker-compose.yml -f docker-compose.dev.yml \
--          exec -T postgres psql -U novelrealm -d novelrealm \
--          < db/migrations/2026-08-05_passage_replies.sql
--
--  Ré-exécutable sans dommage (IF NOT EXISTS partout).
-- =====================================================================

-- Un seul niveau d'imbrication, comme en fin de chapitre : au-delà,
-- l'indentation devient illisible sur un téléphone. Une réponse à une
-- réponse est re-rattachée au même fil, avec un @pseudo — c'est le
-- service qui applique la règle, la colonne ne fait que la permettre.
--
-- ON DELETE CASCADE : supprimer un message emporte ses réponses. Les
-- commentaires de PASSAGE n'ont pas de pierre tombale, contrairement à
-- ceux de fin de chapitre — un fil de trois messages accroché à une
-- ligne n'a pas de conversation à préserver.
ALTER TABLE passage_annotation
    ADD COLUMN IF NOT EXISTS parent_id BIGINT
    REFERENCES passage_annotation(id) ON DELETE CASCADE;

-- Chargement des réponses d'un fil en une requête.
CREATE INDEX IF NOT EXISTS idx_passage_annotation_parent
    ON passage_annotation (parent_id)
    WHERE parent_id IS NOT NULL;
