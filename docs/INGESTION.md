# Ingestion de romans (miroir chikari.moe)

> Comment la base se remplit et se maintient en light novels (métadonnées,
> genres, chapitres texte) depuis **chikari.moe**, via son API JSON. Entièrement
> en Spring Boot, à l'intérieur de l'application principale.
>
> Conception détaillée & décisions : [docs/INGESTION_V2.md](./INGESTION_V2.md).

## TL;DR

L'ingestion tourne **toute seule** : un job planifié (par défaut à 03:00) rapatrie
les nouveaux titres du catalogue chikari et met à jour les chapitres des romans
déjà en base. Objectif à terme : un **miroir complet** du catalogue, atteint par
vagues.

Pour agir à la main (réservé aux emails admin) :

```bash
# Importer / compléter un titre précis tout de suite
curl -X POST https://<host>/api/admin/ingestion/novels/shadow-slave \
     -H "Authorization: Bearer <JWT_ADMIN>"

# Déclencher un cycle complet (découverte + maintenance) maintenant
curl -X POST https://<host>/api/admin/ingestion/sync \
     -H "Authorization: Bearer <JWT_ADMIN>"
```

Le `slug` est la partie d'URL : `https://chikari.moe/novels/`**`shadow-slave`**`/`.
Les deux endpoints répondent **202 Accepted** immédiatement ; le travail se fait
en arrière-plan (un gros titre = plusieurs milliers de chapitres). Le détail part
dans les logs serveur.

## Ce que ça fait

- **Découverte** : parcourt le catalogue (`/api/novels?sort=added`) et importe les
  titres absents de la base, jusqu'à `discovery-cap` par exécution (débit du
  miroir).
- **Maintenance** : pour chaque roman déjà stocké, un appel de détail ; si les
  signaux de fraîcheur `(last_chapter_at, chapter_count, latest_number)` n'ont pas
  bougé, on ne télécharge **aucun** chapitre. Sinon, on ne tire que les nouveaux.
- **Idempotent** : un roman = `(source, source_id)` ; un chapitre =
  `(novel_id, chapter_number)`. Relancer ne crée aucun doublon.
- **Poli & robuste** : temporisation entre chapitres, retry borné sur 429/5xx, et
  une panne de la source ne fait jamais tomber l'app (le prochain cycle réessaie).
- Les chapitres **verrouillés** (premium) et à **numéro décimal** sont sautés
  (loggés) ; les verrouillés sont retentés tant qu'ils ne se débloquent pas.

## La source (chikari.moe)

API JSON publique, famille `/api/novels/*` (light novels **texte** ; la famille
`/api/series/*`, qui est de l'image, n'est pas utilisée). Contrat détaillé dans
[INGESTION_V2.md](./INGESTION_V2.md) :

| Rôle | Endpoint |
|---|---|
| Catalogue | `GET /api/novels?offset&limit` |
| Détail | `GET /api/novels/<slug>` |
| Liste chapitres | `GET /api/novels/<slug>/chapters?offset&limit` |
| Texte d'un chapitre | `GET /api/novels/<slug>/chapters/<number>/read` |

## Configuration

Bloc `novelrealm.ingestion` dans `application.yml` (tout surchargeable par
variable d'environnement) :

| Propriété | Env | Défaut | Rôle |
|---|---|---|---|
| `enabled` | `INGESTION_ENABLED` | `true` | coupe-circuit du job planifié |
| `cron` | `INGESTION_CRON` | `0 0 3 * * *` | horaire du sync |
| `delay-ms` | `INGESTION_DELAY_MS` | `500` | pause entre chapitres (politesse) |
| `catalog-page-size` | `INGESTION_CATALOG_PAGE_SIZE` | `24` | pagination du catalogue |
| `discovery-cap` | `INGESTION_DISCOVERY_CAP` | `100` | nouveaux titres / run (débit du miroir) |
| `skip-nsfw` | `INGESTION_SKIP_NSFW` | `true` | la découverte saute l'adulte |
| `max-retries` | `INGESTION_MAX_RETRIES` | `3` | tentatives sur erreur transitoire |
| `source.base-url` | `INGESTION_SOURCE_BASE_URL` | `https://chikari.moe` | URL de la source |

Accès admin : `app.admin-emails` (`ADMIN_EMAILS`, emails séparés par des virgules,
**vide = personne**).

## Exploitation

- **Aller plus vite / plus lentement** dans le rattrapage : monter/baisser
  `INGESTION_DISCOVERY_CAP`. Attention au stockage (un miroir complet = plusieurs
  Go de texte) et à la politesse envers la source.
- **Suspendre l'ingestion** : `INGESTION_ENABLED=false` (le cron ne fait plus rien ;
  les endpoints admin restent, eux, actionnables).
- **Sécurité** : les endpoints admin exigent un JWT valide **et** un email listé
  dans `ADMIN_EMAILS`. C'est un mécanisme d'intérim (pas de rôles en base) ; voir
  [INGESTION_V2.md](./INGESTION_V2.md).

## Notes de migration (depuis l'ancien système)

- L'ancien scraper HTML **LightNovelWorld** (Jsoup) et la cible `make ingest`
  (conteneur one-shot, profil Spring `ingest`) ont été **retirés** : le site source
  a fermé, et l'ingestion vit désormais dans l'app.
- Schéma : voir `db/migrations/2026-08-25_chikari_ingestion.sql` (colonnes
  `source_*` sur `novels`, `source_number`/`locked` sur `chapters`).
