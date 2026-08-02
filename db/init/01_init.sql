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
    UNIQUE (novel_id, chapter_number)                  -- pas deux fois le même n° pour un roman
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
