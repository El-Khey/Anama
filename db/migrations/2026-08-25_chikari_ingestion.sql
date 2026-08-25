-- =====================================================================
--  Ingestion V2 (chikari.moe) : colonnes d'identité SOURCE + suivi de sync.
--
--  Contexte. lightnovelworld.org a fermé (fusionné dans chikari.moe). Le
--  nouveau système d'ingestion consomme l'API JSON de chikari
--  (/api/novels/*) et vise un MIROIR COMPLET du catalogue, maintenu par un
--  job planifié. Voir docs/INGESTION_V2.md.
--
--  Pour être idempotent (ré-exécutable sans doublon) et savoir « ce roman
--  a-t-il changé chez la source ? » SANS retélécharger ses chapitres, on
--  mémorise sur chaque roman :
--     - son identité source     : source, source_id, source_slug
--     - ses signaux de fraîcheur : source_chapter_count,
--                                  source_latest_number,
--                                  source_last_chapter_at
--     - la date du dernier sync  : last_synced_at
--     - le drapeau adulte        : is_nsfw (pilote le filtrage de découverte)
--  L'unicité (source, source_id) est la VRAIE clé d'upsert (le slug reste
--  unique mais peut entrer en collision entre deux sources — cf. suffixage
--  côté service).
--
--  Côté chapitres :
--     - source_number : le numéro RÉEL de la source (float, ex. 1.5). Notre
--       chapter_number reste INT (référencé partout : tri, reader,
--       progression, contrainte chapters_novel_chapter_uq). On préserve
--       donc le float à part, pour une éventuelle migration future sans
--       re-fetch. Les chapitres à numéro décimal sont ignorés à l'import
--       (ils ne mappent pas vers un INT distinct) ; ils restent rares.
--     - locked : chapitre premium / fenêtre temporelle (corps retenu par la
--       source). On ne le persiste pas tant qu'il est verrouillé ; ce
--       drapeau évite de le recompter éternellement comme « manquant » et
--       permet au reader d'afficher un cadenas.
--
--  STRUCTUREL → « make check-schema » compare db/init/01_init.sql à
--  db/init + db/migrations. Ces mêmes colonnes/index sont donc AUSSI
--  ajoutés dans 01_init.sql, sinon le check passe au rouge.
--
--  Ré-exécutable sans dommage : « ADD COLUMN IF NOT EXISTS » et
--  « CREATE UNIQUE INDEX IF NOT EXISTS » sont nativement idempotents ; au
--  second passage, rien n'est refait.
--
--  (Le drift pré-existant novels.is_featured — colonne sans champ JPA — est
--  laissé tel quel : hors sujet ici.)
-- =====================================================================

-- ── novels : identité source + signaux de fraîcheur ──────────────────
ALTER TABLE novels ADD COLUMN IF NOT EXISTS source                 VARCHAR(32);
ALTER TABLE novels ADD COLUMN IF NOT EXISTS source_id              BIGINT;
ALTER TABLE novels ADD COLUMN IF NOT EXISTS source_slug            VARCHAR(255);
ALTER TABLE novels ADD COLUMN IF NOT EXISTS source_chapter_count   INT;
ALTER TABLE novels ADD COLUMN IF NOT EXISTS source_latest_number   NUMERIC(10,2);
ALTER TABLE novels ADD COLUMN IF NOT EXISTS source_last_chapter_at TIMESTAMPTZ;
ALTER TABLE novels ADD COLUMN IF NOT EXISTS last_synced_at         TIMESTAMPTZ;
ALTER TABLE novels ADD COLUMN IF NOT EXISTS is_nsfw                BOOLEAN NOT NULL DEFAULT FALSE;

-- Un seul roman par (source, source_id) = clé d'idempotence de l'upsert.
-- Index PARTIEL : les romans hérités (source NULL, ère lightnovelworld) en
-- sont exemptés — ils n'ont pas d'identité source et ne doivent pas se
-- télescoper sur (NULL, NULL).
CREATE UNIQUE INDEX IF NOT EXISTS novels_source_id_uq
    ON novels (source, source_id) WHERE source IS NOT NULL;

-- ── chapters : numéro source réel + verrou ───────────────────────────
ALTER TABLE chapters ADD COLUMN IF NOT EXISTS source_number NUMERIC(10,2);
ALTER TABLE chapters ADD COLUMN IF NOT EXISTS locked        BOOLEAN NOT NULL DEFAULT FALSE;
