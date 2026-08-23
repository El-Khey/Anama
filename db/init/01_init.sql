-- =====================================================================
--  Script d'initialisation de la base Novel Realm (PostgreSQL).
--
--  Exécuté AUTOMATIQUEMENT par le conteneur Postgres, UNE SEULE FOIS :
--  à la première création de la base (quand le volume de données est vide).
--  → Pour repartir de zéro : `make clean` (supprime le volume), puis
--    `make dev` / `make db`.
--
--  Convention : fichiers préfixés (01_, 02_, …), exécutés dans l'ordre
--  alphabétique. Les CREATE TABLE sont ordonnés selon les dépendances (FK).
--
--  Timestamps : created_at/updated_at ne sont posés QUE là où une
--  fonctionnalité ou un audit en a besoin. Les tables de référence (genres)
--  et de jointure pure (novel_genre, category_novel) n'en ont pas.
-- =====================================================================

-- ─────────────────────────── Utilisateurs ───────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id          BIGSERIAL PRIMARY KEY,
    pseudo      VARCHAR(255) NOT NULL,
    email       VARCHAR(255) NOT NULL UNIQUE,
    password    VARCHAR(255),                          -- NULL pour un compte OAuth
    provider    VARCHAR(20)  NOT NULL DEFAULT 'LOCAL'
                CHECK (provider IN ('LOCAL', 'GOOGLE')),
    bio         TEXT,                                  -- bio courte du profil (nullable)
    avatar_url  VARCHAR(500),                          -- /uploads/avatars/… ou URL Google
    banner_url  VARCHAR(500),                          -- bannière de profil (optionnelle)
    preferences TEXT,                                  -- préférences JSON (accent, lecteur…)
    created_at  TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP NOT NULL
);

-- ──────────────────────────── Catalogue ─────────────────────────────
CREATE TABLE IF NOT EXISTS novels (
    id              BIGSERIAL PRIMARY KEY,
    slug            VARCHAR(255) UNIQUE,               -- identifiant source (scraping) ; clé d'idempotence
    title           VARCHAR(255) NOT NULL,
    author          VARCHAR(255) NOT NULL,
    description     TEXT NOT NULL,
    cover_image_url VARCHAR(255),
    status          VARCHAR(20) NOT NULL
                    CHECK (status IN ('ONGOING', 'COMPLETED')),
    is_featured     BOOLEAN NOT NULL DEFAULT FALSE,    -- "à la une" sur l'Accueil
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL
);

-- Chapitres rattachés directement au roman (structure plate, web novel).
CREATE TABLE IF NOT EXISTS chapters (
    id             BIGSERIAL PRIMARY KEY,
    novel_id       BIGINT NOT NULL REFERENCES novels(id) ON DELETE CASCADE,
    chapter_number INT NOT NULL,                       -- ordre dans le roman
    title          VARCHAR(255) NOT NULL,
    content        TEXT NOT NULL,
    created_at     TIMESTAMP NOT NULL,                 -- "derniers chapitres" (Accueil)
    updated_at     TIMESTAMP NOT NULL,
    -- Pas deux fois le même n° pour un roman. Contrainte NOMMÉE explicitement :
    -- laissée anonyme, PostgreSQL la baptise « chapters_novel_id_chapter_number_key »,
    -- alors que les bases déjà en service portent « chapters_novel_chapter_uq ». Même
    -- contrainte, deux noms — et un nom, ça se référence (ON CONFLICT ON CONSTRAINT,
    -- suppression d'index…). On fige donc celui qui existe déjà.
    CONSTRAINT chapters_novel_chapter_uq UNIQUE (novel_id, chapter_number)
);

-- Genres : table de référence (Fantasy, Romance, …).
CREATE TABLE IF NOT EXISTS genres (
    id    BIGSERIAL PRIMARY KEY,
    name  VARCHAR(100) NOT NULL UNIQUE
);

-- Jointure M:N  novels ⇄ genres.
CREATE TABLE IF NOT EXISTS novel_genre (
    novel_id  BIGINT NOT NULL REFERENCES novels(id) ON DELETE CASCADE,
    genre_id  BIGINT NOT NULL REFERENCES genres(id) ON DELETE CASCADE,
    PRIMARY KEY (novel_id, genre_id)
);

-- ───────────────────────── Bibliothèque perso ───────────────────────
-- Romans suivis par un utilisateur, avec son statut de lecture.
CREATE TABLE IF NOT EXISTS library_entry (
    user_id   BIGINT NOT NULL REFERENCES users(id)  ON DELETE CASCADE,
    novel_id  BIGINT NOT NULL REFERENCES novels(id) ON DELETE CASCADE,
    status    VARCHAR(20) NOT NULL
              CHECK (status IN ('PLAN_TO_READ', 'READING', 'COMPLETED', 'PAUSED')), 
    added_at  TIMESTAMP NOT NULL,                      -- tri "date d'ajout"
    PRIMARY KEY (user_id, novel_id)
);

-- Étagères créées par l'utilisateur pour ranger sa bibliothèque.
CREATE TABLE IF NOT EXISTS categories (
    id       BIGSERIAL PRIMARY KEY,
    user_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name     VARCHAR(100) NOT NULL,
    UNIQUE (user_id, name)                             -- pas deux étagères de même nom
);

-- Jointure M:N  categories ⇄ novels.
CREATE TABLE IF NOT EXISTS category_novel (
    category_id  BIGINT NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    novel_id     BIGINT NOT NULL REFERENCES novels(id)     ON DELETE CASCADE,
    PRIMARY KEY (category_id, novel_id)
);

-- ───────────────────────── Progression de lecture ───────────────────
-- État de lecture d'un chapitre pour un utilisateur (lu, position, date).
CREATE TABLE IF NOT EXISTS chapter_progress (
    user_id          BIGINT NOT NULL REFERENCES users(id)    ON DELETE CASCADE,
    chapter_id       BIGINT NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,
    is_read          BOOLEAN NOT NULL DEFAULT FALSE,
    scroll_position  INT NOT NULL DEFAULT 0,            -- reprise dans le chapitre
    read_at          TIMESTAMP NOT NULL,                -- historique / reprendre / tri
    PRIMARY KEY (user_id, chapter_id)
);

-- ───────────────────────── Favoris de chapitre ──────────────────────
-- Marque-pages : chapitres mis en favori par un utilisateur.
CREATE TABLE IF NOT EXISTS chapter_favorite (
    user_id       BIGINT NOT NULL REFERENCES users(id)    ON DELETE CASCADE,
    chapter_id    BIGINT NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,
    favorited_at  TIMESTAMP NOT NULL,                    -- tri "ajoutés récemment"
    PRIMARY KEY (user_id, chapter_id)
);

-- ──────────────────────── Avis sur un roman ─────────────────────────
-- Une note (1 à 5 étoiles) + un commentaire facultatif. UN SEUL avis par
-- couple (utilisateur, roman) : on modifie le sien, on n'en empile pas.
CREATE TABLE IF NOT EXISTS review (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT   NOT NULL REFERENCES users(id)  ON DELETE CASCADE,
    novel_id    BIGINT   NOT NULL REFERENCES novels(id) ON DELETE CASCADE,
    rating      SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    body        TEXT,                                    -- commentaire (facultatif)
    created_at  TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP NOT NULL,
    UNIQUE (user_id, novel_id)
);

-- Liste des avis d'un roman, les plus récents d'abord (page « fiche »).
CREATE INDEX IF NOT EXISTS idx_review_novel_created
    ON review (novel_id, created_at DESC);

-- ─────────────────── Commentaires de fin de chapitre ────────────────
-- Messages laissés sous un chapitre (#41). Contrairement à `review`, un
-- lecteur peut en écrire autant qu'il veut sur le même chapitre.
--
-- parent_id : NULL = le message ouvre un fil ; sinon il pointe TOUJOURS
-- vers un message racine. Le service refuse d'aller plus profond — au-delà
-- d'un niveau, l'indentation devient illisible sur un téléphone.
--
-- deleted_at : suppression DOUCE. Effacer la ligne détacherait les réponses
-- accrochées dessus. Un message supprimé sans réponse sort simplement de la
-- liste ; s'il en porte encore, il y reste en « pierre tombale ».
CREATE TABLE IF NOT EXISTS chapter_comment (
    id          BIGSERIAL PRIMARY KEY,
    chapter_id  BIGINT NOT NULL REFERENCES chapters(id)     ON DELETE CASCADE,
    user_id     BIGINT NOT NULL REFERENCES users(id)        ON DELETE CASCADE,
    parent_id   BIGINT          REFERENCES chapter_comment(id) ON DELETE CASCADE,
    body        TEXT NOT NULL,
    -- Réservée à l'étape « anti-spoil » de #41 : la colonne est posée
    -- maintenant pour s'épargner une migration, l'API ne l'expose pas encore.
    is_spoiler  BOOLEAN NOT NULL DEFAULT FALSE,
    -- GIF joint (issue #45, §5) : deux URL du fournisseur (animée + image figée),
    -- jamais un fichier chez nous. NULL = message texte ordinaire ; quand
    -- gif_url est posée, body PEUT être vide (règle tenue par le service).
    gif_url         VARCHAR(500),
    gif_preview_url VARCHAR(500),
    created_at  TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP NOT NULL,
    deleted_at  TIMESTAMP                                   -- NULL = message vivant
);

-- Fils d'un chapitre, les plus récents d'abord (liste du lecteur).
CREATE INDEX IF NOT EXISTS idx_chapter_comment_chapter_created
    ON chapter_comment (chapter_id, created_at DESC);

-- Réponses d'un fil : chargées en UN appel pour toute une page de fils.
CREATE INDEX IF NOT EXISTS idx_chapter_comment_parent
    ON chapter_comment (parent_id);

-- Garde-fou anti-rafale : « cet utilisateur a-t-il écrit ces 15 dernières secondes ? »
CREATE INDEX IF NOT EXISTS idx_chapter_comment_user_created
    ON chapter_comment (user_id, created_at DESC);

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
    -- GIF joint (issue #45, §5 — commentaires uniquement) : mêmes règles
    -- qu'en fin de chapitre.
    gif_url         VARCHAR(500),
    gif_preview_url VARCHAR(500),
    -- Réponse à un autre commentaire de passage (#41, §4). Un seul niveau :
    -- au-delà, l'indentation devient illisible sur un téléphone. Supprimer
    -- un message emporte ses réponses — un fil de trois messages accroché à
    -- une ligne n'a pas de conversation à préserver.
    parent_id     BIGINT REFERENCES passage_annotation(id) ON DELETE CASCADE,
    created_at    TIMESTAMP NOT NULL
);

-- Page « Mes citations » : les miennes, les plus récentes d'abord.
CREATE INDEX IF NOT EXISTS idx_passage_annotation_user_kind_created
    ON passage_annotation (user_id, kind, created_at DESC);

-- Résolution d'ancre (« aller au passage »).
CREATE INDEX IF NOT EXISTS idx_passage_annotation_chapter_block
    ON passage_annotation (chapter_id, block_index);

-- Agrégats par bloc (#41, §4) : l'écran de lecture demande en UN appel
-- l'activité de tous les blocs du chapitre. `kind` en deuxième position
-- rend réactions et commentaires contigus, sans relire les citations.
CREATE INDEX IF NOT EXISTS idx_passage_annotation_chapter_kind_block
    ON passage_annotation (chapter_id, kind, block_index);

-- Les réactions de bloc sont MULTI-EMOJI (comme sur Discord) : un lecteur peut
-- cumuler 👍 et 🔥 sur le même paragraphe, une ligne REACTION par emoji. Il n'y
-- a donc PAS d'index d'unicité « une réaction par passage » — il a existé puis
-- a été retiré (db/migrations/2026-08-22_block_reactions_multi.sql). Poser deux
-- fois le même emoji est empêché côté service (le geste est un bascule), pas par
-- une contrainte. L'agrégat s'appuie sur idx_passage_annotation_chapter_kind_block
-- ci-dessus.

-- Fil d'un passage, du plus ancien au plus récent : une discussion se lit
-- dans l'ordre où elle s'est tenue. Interrogé par EMPREINTE — cherché à
-- l'index, un fil reviendrait vide après une ré-ingestion alors que les
-- messages sont toujours là (#41, §2 : jamais de passage perdu en silence).
CREATE INDEX IF NOT EXISTS idx_passage_annotation_thread
    ON passage_annotation (chapter_id, text_hash, kind, created_at);

-- Les réponses d'un message racine (#41, §4). Sans cet index, afficher un fil
-- parcourt toute la table pour retrouver les réponses de chaque message.
--
-- Il manquait ici alors que db/migrations/2026-08-05_passage_replies.sql le
-- crée : une base neuve avait donc la colonne parent_id — déclarée plus haut
-- dans le CREATE TABLE — mais pas son index. Exemple concret de ce que coûte
-- de décrire le même schéma à deux endroits ; « make check-schema » compare
-- désormais les deux chemins pour que ça ne repasse plus inaperçu.
CREATE INDEX IF NOT EXISTS idx_passage_annotation_parent
    ON passage_annotation (parent_id);

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

-- ──────────────── Réactions emoji sur les commentaires ───────────────
-- Le pendant, sur les COMMENTAIRES, des réactions déjà présentes sur les
-- PASSAGES (passage_annotation.emoji). Plusieurs emojis par lecteur sont
-- permis (façon Discord) ; poser deux fois le même n'en compte qu'un.
--
-- Polymorphe comme comment_mention : source_kind + source_id désignent la
-- ligne (chapter_comment ou passage_annotation), pas de FK possible — les
-- services purgent les réactions des commentaires de passage supprimés.
-- L'emoji est stocké tel quel : le bouton « + » ouvre le clavier complet,
-- la validation est côté service, pas en base.
-- Détail complet et rationale : db/migrations/2026-08-22_comment_reactions.sql
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

-- Les réactions d'une page de fils, en une requête.
CREATE INDEX IF NOT EXISTS idx_comment_reaction_source
    ON comment_reaction (source_kind, source_id);

-- Votes (pouce vert / pouce rouge) sur les commentaires.
-- Polymorphe comme comment_reaction : source_kind + source_id désignent la
-- ligne, pas de FK possible — les services purgent les votes des commentaires
-- supprimés. value ∈ {-1, +1} : « neutre » = absence de ligne, jamais 0.
-- UNIQUE (source_kind, source_id, user_id) : un seul vote par lecteur et par
-- message (changer d'avis met à jour la ligne, revoter le même sens la retire).
-- Détail complet et rationale : db/migrations/2026-08-23_comment_votes.sql
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

-- Les votes d'une page de fils, en une requête.
CREATE INDEX IF NOT EXISTS idx_comment_vote_source
    ON comment_vote (source_kind, source_id);

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
