# Ingestion V2 — Plan & conception (chikari.moe)

> **Statut : IMPLÉMENTÉ le 2026-08-25.** Ce document a servi de plan et reste la
> référence de conception. Guide d'exploitation : [INGESTION.md](./INGESTION.md).
> Reste à faire côté ops : lancer `make check-schema` (Postgres up) et renseigner
> `ADMIN_EMAILS`.

## 1. Pourquoi on refait l'ingestion

L'ingestion actuelle (`LightNovelWorldScraper`, Jsoup) cible **lightnovelworld.org,
un site mort** : il a fusionné dans chikari.moe, ses URL d'images redirigent en 301,
et ses sélecteurs CSS ne matchent plus rien. La migration
`db/migrations/2026-08-22_chikari_cover_urls.sql` documente déjà cette mort.

Donc ce n'est pas « réparer le scraper » : c'est **construire une nouvelle source**,
maintenable et pro, pointée sur chikari.moe.

## 2. La source : chikari.moe (API JSON, vérifiée)

chikari.moe est une **app SvelteKit avec une vraie API JSON publique** — plus de
scraping HTML fragile. Elle héberge à la fois des webtoons (`/api/series/*`, images)
**et des light novels en texte** (`/api/novels/*`). **On n'utilise QUE la famille
`/api/novels/*`.** Novel-Realm reste un lecteur de light novels texte, point.

Tous les endpoints ci-dessous ont été confirmés par de vrais appels :

| Besoin | Endpoint | Champs utiles |
|---|---|---|
| Énumérer le catalogue | `GET /api/novels?offset=N&limit=24` | `items[]` (id, slug, title, status, chapter_count, cover_url, `last_chapter_at`, `medium`), `total` (2316) |
| Détail d'un novel | `GET /api/novels/<slug>` | `id` (stable), description, `reading_mode:"text"`, `medium:"novel"`, `status`, `is_nsfw`, `latest_number`, `chapter_count`, `last_chapter_at`, `genres[]`, `authors[]{role}` |
| Liste des chapitres | `GET /api/novels/<slug>/chapters?offset&limit=100` | `items[]{number, volume, title, lang, created_at}`, `total` |
| **Texte d'un chapitre** | `GET /api/novels/<slug>/chapters/<number>/read` | **`body`** (prose), `title`, `locked`, `prev/next_number` |

Faits qui pilotent le design :

- **Idempotence** : `id` numérique **stable** + `slug` → clés d'upsert propres.
- **Énumération complète** : `/api/novels?offset=…` pagine **tout le catalogue**
  (`total` = 2316) → base du miroir complet (rattraper tout ce qui manque).
- **Détection d'incrément** (clé du sync planifié) : le trio
  `(last_chapter_at, chapter_count, latest_number)` du détail permet de savoir
  « rien n'a changé » **sans tirer aucun chapitre** (maintenance quasi gratuite).
- **Chapitres `locked`** (premium/fenêtre temporelle) : `body` retenu → on ne les
  persiste pas, on réessaie au prochain run.
- **Numéros de chapitre** : l'API les renvoie en `float`. Sur **4800 chapitres de
  8 novels texte vérifiés → 0 décimal.** En pratique ce sont des entiers ; on garde
  un garde-fou (voir §4, décision « chapter_number »).
- **Politesse** : c'est le site de quelqu'un d'autre. On throttle, on met un
  User-Agent navigateur réel (les UA de bot étaient bloqués sur l'ancien site), on
  gère 429/5xx sans planter.

## 3. Décisions produit (déjà tranchées)

1. **Novel-Realm reste un lecteur de light novels texte.** Modèle inchangé
   (`content TEXT`). Pas de modèle image/pages.
2. **Déclenchement = job interne `@Scheduled`** dans l'app principale (plus de
   conteneur jetable pour le sync récurrent).
3. **Périmètre = MIROIR COMPLET de chikari.** À terme, l'app rapatrie **tout novel
   présent sur chikari et absent de l'app** (~2316 titres), puis les maintient.
   Concrètement ça se fait en **deux phases** (voir §5.2.1) :
   - **Backfill** : rattrapage initial de tout le catalogue, par vagues, sur
     plusieurs nuits (rythme réglable) — on ne peut pas tout tirer d'un coup
     (millions de chapitres).
   - **Régime permanent** : une fois le catalogue rattrapé, capter les nouveautés
     (`?sort=added`) + mettre à jour les chapitres des novels existants.
4. **Ajout initial d'un titre = endpoint admin REST** protégé (pour prioriser un
   titre voulu tout de suite, sans attendre que le backfill y arrive).

## 4. Contraintes réelles du code (repérées, pas devinées)

Ces points changent l'implémentation — ils viennent d'une lecture du code existant :

- **Jackson 3** : le projet importe `tools.jackson.*` (pas `com.fasterxml.jackson`),
  et n'utilise **aucun** `@JsonProperty`. → On bindera sur `JsonNode` et on mappera
  à la main (comme `GifService` le fait déjà), plutôt qu'un jeu de DTO annotés.
- **`RestClient.builder()` doit être appelé en direct**, jamais injecté (Spring Boot 4
  a sorti le bean auto-configuré dans un starter qu'on n'a pas). On copie le pattern
  exact de `GifService` (baseUrl + User-Agent + `JdkClientHttpRequestFactory` avec
  timeouts).
- **Pas de rôle admin** : le filtre JWT donne des authorities vides, le principal =
  l'email. → La protection admin sera une **allow-list d'emails en config**, vérifiée
  dans le contrôleur. (Inventer un `ROLE_ADMIN` serait du sur-engineering pour 2
  endpoints ; ce sera un chantier séparé si besoin.)
- **`make check-schema`** vérifie que `db/init/01_init.sql` **==** `init + migrations`.
  → Toute migration **structurelle** doit être **répercutée dans `01_init.sql`**,
  sinon le check passe au rouge. (C'est le piège le plus facile à oublier.)
- **`open-in-view: false`** : le service tourne hors requête web → les relations lazy
  se touchent dans une transaction ou en eager. On garde le modèle « save par item »
  de l'ancien service (voir §5, transactions).
- **Aucune dépendance à ajouter** : `RestClient` (spring-web), Jackson 3, `JdbcTemplate`
  (data-jpa), `@Scheduled`/`@Async` (spring-context) sont déjà là. Le seul changement
  de dépendance est un **retrait** de Jsoup une fois le vieux scraper supprimé.

## 5. Architecture cible

### 5.1 Adaptateur de source (abstraction minimale)

```
com.novelrealm.ingestion.source
├── SourceAdapter                (interface)
├── ChikariSourceAdapter         (@Component, RestClient façon GifService)
└── dto/  SourceCatalogPage, SourceNovel, SourceChapterRef,
          SourceChapterPage, SourceChapterBody
```

```java
public interface SourceAdapter {
    String sourceKey();                                  // "chikari"
    SourceCatalogPage listCatalog(String sort, int offset, int limit);
    SourceNovel       fetchNovel(String slug);
    SourceChapterPage listChapters(String slug, int offset, int limit);
    SourceChapterBody fetchChapterBody(String slug, BigDecimal number);
}
```

Une interface + une implémentation. Elle existe pour (a) garder le service de sync
ignorant du JSON chikari, et (b) donner un sens à la colonne `source` le jour où une
2ᵉ source arrive. Pas de registry/factory : pas besoin aujourd'hui.

### 5.2 Service de sync

```
com.novelrealm.ingestion
├── NovelSyncService            (@Service)   importNovel / syncExisting / discoverNew / runFullSync
├── IngestionProperties         (@ConfigurationProperties "novelrealm.ingestion")
└── SourceUnavailableException
```

- **`importNovel(slug)`** — ajout initial : upsert du novel + genres, pagination de
  tous les chapitres, fetch+persist des `body`, **skip `locked`/vide** (retry-later),
  throttle. Idempotent (re-run = ne re-fait rien).
- **`syncExisting()`** — pour chaque novel `source=chikari` : 1 appel détail, puis
  **court-circuit** si le trio `(last_chapter_at, chapter_count, latest_number)` est
  inchangé (0 appel chapitre). Sinon, ne tire que les chapitres manquants (> max stocké,
  + rattrapage des ex-`locked`). Chaque novel dans son propre try/catch → un titre
  cassé n'arrête pas le run.
- **`discoverNew(cap)`** — voir la stratégie miroir complet ci-dessous (§5.2.1) :
  parcourt le catalogue, ajoute les titres inconnus jusqu'à un plafond par run.
- **`runFullSync()`** — `discoverNew(cap)` puis `syncExisting()`, appelé par le
  scheduler et par l'endpoint admin `/sync`.

#### 5.2.1 Stratégie miroir complet (backfill → régime permanent)

Objectif : **tout chikari, à terme**. Mais « tout d'un coup » est impossible et impoli
(millions de chapitres). Donc deux phases, gérées par le même `discoverNew(cap)` :

- **Phase backfill (rattrapage)** : tant que le catalogue n'est pas entièrement en
  base, `discoverNew` **pagine tout le catalogue** (`/api/novels?offset=0…total`) et
  importe tout titre inconnu, jusqu'à `cap` titres par run. Avec `cap` = 100-200/nuit,
  le miroir complet est atteint en quelques semaines. Un curseur (`discovery_offset`
  ou simplement « premier titre du catalogue pas encore en base ») permet de reprendre
  là où on s'est arrêté d'une nuit à l'autre.
- **Phase régime permanent** : une fois le catalogue rattrapé, `discoverNew` bascule
  sur `?sort=added` (les nouveautés récentes) — bien plus léger — et `syncExisting`
  maintient les chapitres des titres déjà stockés.

`cap` (`discovery-cap`) est le **débit** du miroir : haut = rattrapage rapide mais
crawl plus lourd ; bas = doux mais plus long. Réglable en config, sans redéploiement.

> **⚠️ Implication stockage.** Un miroir complet de ~2316 novels texte = potentiellement
> **plusieurs Go** en base Postgres (c'est du TEXT, pas des images — gérable, mais un
> ordre de grandeur au-dessus d'un catalogue curaté). À dimensionner côté hébergement.
> **⚠️ Politesse.** Le backfill est un gros crawl d'un site tiers ; le throttle
> `delay-ms` et un `cap` raisonnable sont ce qui garde l'app « bien élevée ».

**Mappings** : `status` `releasing`→`ONGOING`, `completed`→`COMPLETED` ;
`authors[]` → on prend le `role=="author"`, sinon le premier, sinon `"Inconnu"`
(le champ est `NOT NULL`) ; `genres[].name` → find-or-create existant.

**Transactions** : on **garde le modèle non-`@Transactional`, save par chapitre**
(comme l'ancien service). Un crash au chapitre 1800/3000 laisse 1799 durablement en
base, et le run suivant reprend. Une transaction géante autour de 3000 chapitres
serait une écriture longue et perdrait tout à la moindre erreur.

### 5.3 Schéma / migration (§ le plus sensible)

Fichiers modifiés **ensemble** (sinon `check-schema` casse) :
- `db/migrations/2026-08-25_chikari_ingestion.sql` (nouveau)
- `db/init/01_init.sql` (miroir des mêmes colonnes/index)

Colonnes `novels` (idempotent, `ADD COLUMN IF NOT EXISTS`) :
`source`, `source_id`, `source_slug`, `source_chapter_count`, `source_latest_number`,
`source_last_chapter_at`, `last_synced_at`, `is_nsfw` + index unique **partiel**
`(source, source_id) WHERE source IS NOT NULL` (les anciennes lignes à `source NULL`
sont exemptées).

Colonnes `chapters` : `source_number NUMERIC(10,2)` (préserve le vrai float) + `locked`.

**Décision `chapter_number`** : on **garde `INT`** (référencé partout : tri, reader,
progression, contrainte unique `chapters_novel_chapter_uq`). Migrer en `NUMERIC`
rippleraeit sur tout le reader — trop invasif pour un cas quasi inexistant. Les rares
chapitres décimaux (`1.5`) sont **skippés + loggés** ; leur vraie valeur est conservée
dans `source_number` pour une éventuelle migration future sans re-fetch.

**On ne stocke PAS** : `rating`/`views` (Novel-Realm calcule ses propres notes via sa
table `Review` — deux sources de vérité = confusion), `cover_thumbhash`, `lang`,
`volume` (reader plat, texte). Minimal et aligné sur ce que l'app affiche vraiment.

### 5.4 Scheduling

- `config/SchedulingConfig` : `@Configuration @EnableScheduling` (+ `@EnableAsync` et
  un petit `ThreadPoolTaskExecutor` pour l'admin async).
- `ingestion/IngestionScheduler` : `@Scheduled(cron="${novelrealm.ingestion.cron:0 0 3 * * *}")`
  → `runFullSync()` gardé par un verrou.
- **Anti-double-run multi-instance** : `pg_try_advisory_lock` (helper `PgAdvisoryLock`
  via `JdbcTemplate`, déjà dispo). Si un autre replica tourne → ce tick passe son tour.
  Pas de ShedLock/Quartz à ajouter.

### 5.5 Endpoint admin REST

- `POST /api/admin/ingestion/novels/{slug}` → import initial d'un titre.
- `POST /api/admin/ingestion/sync` → déclenche un run complet à la main.
- **Sous `/api/`** (pas `/admin/` nu) pour obtenir un vrai 401 non-authentifié via
  `SecurityConfig`, comme le reste de l'API.
- **Sécurité** : allow-list `app.admin-emails` (défaut vide = fail-closed), vérifiée
  contre `authentication.getName()`. 403 sinon.
- **Async** : un import de 3000 chapitres × 500 ms ≈ 25 min → dépasse tout timeout HTTP.
  Le contrôleur répond **202 Accepted** immédiatement ; le job logue son résultat.
  Le `/sync` manuel réutilise le même verrou (pas de double run).

### 5.6 Robustesse / politesse

- Throttle `delay-ms` (défaut 500) entre fetchs de `body`.
- Retry borné maison dans l'adaptateur (429/5xx/timeout, 3 essais, backoff
  exponentiel), puis `SourceUnavailableException`. (spring-retry pas ajouté pour une
  seule boucle.)
- `locked` jamais persisté, recompté chaque run (au cas où ça se débloque).
- Source 404/500 : catch par-novel dans `syncExisting`, catch au sommet du job →
  **une panne de la source ne crash jamais l'app**, le cron suivant réessaie.
- `skip-nsfw` (défaut true) : la **découverte** saute les `is_nsfw` ; un ajout admin
  explicite reste possible (override).
- Logs structurés : `added / updated / skippedExisting / skippedLocked /
  skippedDecimal / failed` par run.

### 5.7 Config (à ajouter dans `application.yml`)

```yaml
novelrealm:
  ingestion:
    enabled: ${INGESTION_ENABLED:true}
    cron: ${INGESTION_CRON:0 0 3 * * *}          # 03:00 quotidien
    delay-ms: ${INGESTION_DELAY_MS:500}
    catalog-page-size: 24
    discovery-cap: ${INGESTION_DISCOVERY_CAP:100} # débit du miroir : nb titres importés/run
    skip-nsfw: ${INGESTION_SKIP_NSFW:true}
    max-retries: 3
    source:
      key: chikari
      base-url: https://chikari.moe
      user-agent: "Mozilla/5.0 ... Chrome/120.0.0.0 Safari/537.36"
app:
  admin-emails: ${ADMIN_EMAILS:}                 # emails admin, séparés par virgules
```

(+ entrées miroir dans `additional-spring-configuration-metadata.json`.)

## 6. Ce qu'on retire

- **Supprime** le scraper mort et ses DTO : `LightNovelWorldScraper`,
  `NovelIngestionService`, `ScrapedNovel/ScrapedChapterRef/ScrapedChapter`.
- **Retire** `IngestionRunner` + le profil Spring `ingest` + la cible `make ingest`
  (l'ajout initial passe par l'endpoint admin, la maintenance par le scheduler).
- **Retire Jsoup** de `build.gradle` (plus aucun autre usage) — après un `grep` final.
- **Réécrit** `docs/INGESTION.md` pour décrire le nouveau système.

## 7. Plan par étapes (ordre d'implémentation)

1. **Schéma** — migration + miroir `01_init.sql`, `make check-schema` vert.
2. **Adaptateur** — `SourceAdapter` + `ChikariSourceAdapter` + DTO neutres.
3. **Service** — `NovelSyncService` + `IngestionProperties` + méthodes repo.
4. **Scheduling** — `SchedulingConfig` + `IngestionScheduler` + `PgAdvisoryLock`.
5. **Admin** — `AdminIngestionController` + allow-list + async 202.
6. **Nettoyage** — suppression scraper/Jsoup/`make ingest`, réécriture docs.

## 8. Risques & points de vigilance

- **`check-schema` (haut)** — migration structurelle à répercuter dans `01_init.sql`,
  sinon rouge. Les deux fichiers changent ensemble.
- **Décimaux (moyen)** — `chapter_number INT` ⇒ chapitres `1.5` skippés/loggés,
  `source_number` garde la vraie valeur. À revoir seulement si les décimaux s'avèrent
  fréquents.
- **Collision de slug (moyen)** — `novels.slug` est unique global (URL du reader).
  Upsert sur `(source, source_id)` d'abord ; si un slug chikari entre en collision
  avec un titre différent, on suffixe (`-chikari`). Garde-fou dans `upsertNovel`.
- **Pas de rôle admin (moyen)** — allow-list email = intérim fail-closed ; vrai modèle
  de rôles = chantier séparé.
- **Verrou best-effort (bas)** — `pg_try_advisory_lock` empêche les runs concurrents,
  ne les met pas en file. `/sync` pendant le run nocturne renvoie « déjà en cours ».
- **Pas de suivi de job (bas)** — l'import admin async renvoie 202 + logue ; pas de
  polling de statut aujourd'hui. Une table `sync_run` serait une bonne évolution.
