-- =====================================================================
--  Réactions et commentaires sur un passage (issue #41, §4).
--
--  POURQUOI CE FICHIER : `db/init/01_init.sql` n'est joué qu'UNE fois, à
--  la création du volume Postgres. Une base déjà remplie ne le rejouera
--  jamais — il faut donc appliquer le changement à la main.
--
--  À jouer sur une base EXISTANTE :
--      docker compose -p novelrealm -f docker-compose.yml -f docker-compose.dev.yml \
--          exec -T postgres psql -U novelrealm -d novelrealm \
--          < db/migrations/2026-08-05_passage_social.sql
--
--  Sur une base neuve (`make clean` puis `make dev`), ne rien faire :
--  01_init.sql contient déjà ces index. Le script est de toute façon
--  ré-exécutable sans dommage (IF NOT EXISTS partout).
--
--  AUCUNE COLONNE N'EST AJOUTÉE : la table `passage_annotation` avait été
--  dessinée dès les citations pour accueillir les quatre usages (`kind`,
--  `emoji`, `body` existent déjà). Cette migration ne pose que les index
--  et la règle d'unicité que le nouvel usage rend nécessaires.
-- =====================================================================

-- ─────────────── Agrégats par bloc d'un chapitre (#41, §4) ───────────
-- L'écran de lecture demande, en UN appel, l'activité de tous les blocs
-- du chapitre. La requête filtre sur (chapitre, genre) puis groupe par
-- bloc : l'index existant (chapter_id, block_index) obligeait à relire
-- les citations et surlignages pour les écarter ensuite. Avec `kind` en
-- deuxième position, réactions et commentaires sont contigus.
CREATE INDEX IF NOT EXISTS idx_passage_annotation_chapter_kind_block
    ON passage_annotation (chapter_id, kind, block_index);

-- ─────────────── Une seule réaction par lecteur et par passage ───────
-- Choix de conception, pas limitation technique : la marge n'affiche
-- qu'un compteur, et laisser quelqu'un poser les six emojis sur le même
-- paragraphe fausserait l'agrégat sans rien exprimer de plus. Toucher un
-- autre emoji REMPLACE le précédent, toucher le même le retire.
--
-- La clé est l'EMPREINTE, pas l'index : un chapitre ré-ingéré décale ses
-- blocs, et une contrainte posée sur l'index laisserait un même lecteur
-- réagir deux fois au même paragraphe. L'empreinte, elle, suit le texte.
--
-- Index partiel : la contrainte ne concerne que les réactions. Les
-- citations, elles, peuvent être multiples sur un même passage.
--
-- NEUTRALISÉ (2026-08-23) — cet index unique « une réaction par passage et
-- par lecteur » est devenu FAUX depuis le passage au multi-emoji : un lecteur
-- peut désormais poser plusieurs réactions sur le même passage. La migration
-- 2026-08-22_block_reactions_multi.sql le supprime (DROP INDEX IF EXISTS).
--
-- Problème : `make migrate` rejoue TOUTES les migrations à chaque fois. Sur une
-- base qui contient déjà des réactions multi-emoji, ce CREATE UNIQUE INDEX
-- échouait (« Key ... is duplicated ») AVANT d'atteindre le DROP du 08-22, et
-- cassait toute la migration. On le retire donc ici : il n'est plus dans
-- 01_init.sql, il est droppé par le 08-22, aucun code n'en dépend — le schéma
-- final est identique, la migration redevient simplement rejouable.
--
-- CREATE UNIQUE INDEX IF NOT EXISTS uq_passage_annotation_one_reaction_per_passage
--     ON passage_annotation (user_id, chapter_id, text_hash)
--     WHERE kind = 'REACTION';

-- ─────────────── Fil d'un passage, du plus ancien au plus récent ─────
-- Interrogé par EMPREINTE et non par index, pour la même raison : après
-- une ré-ingestion, un fil cherché à l'index reviendrait vide alors que
-- les messages sont toujours là. Le §2 de l'issue l'interdit — on ne
-- perd jamais un passage en silence.
CREATE INDEX IF NOT EXISTS idx_passage_annotation_thread
    ON passage_annotation (chapter_id, text_hash, kind, created_at);
