# =====================================================================
#  Makefile : raccourcis pour piloter Docker sans retenir les longues
#  commandes compose. Tape simplement `make <cible>`.
#  (Utilise le plugin Docker Compose v2 → commande "docker compose".)
# =====================================================================

# La commande compose et les combinaisons de fichiers pour chaque mode.
# "-p novelrealm" fixe le NOM DE PROJET Compose : il sert de préfixe aux
# conteneurs, volumes et réseau (novelrealm_postgres_data, etc.). Sans lui,
# Compose prendrait le nom du dossier courant — on le fige donc explicitement.
COMPOSE := docker compose -p novelrealm
DEV     := -f docker-compose.yml -f docker-compose.dev.yml
PROD    := -f docker-compose.yml

# Nombre max de chapitres importés par `make ingest` (surchargeable : MAX=...).
MAX     ?= 50

# Cible par défaut quand on tape juste `make` : afficher l'aide.
.DEFAULT_GOAL := help

.PHONY: help dev prod down logs ps db restart-api rebuild clean ingest backup restore backups migrate check-schema

# Où atterrissent les sauvegardes de la base (dossier non versionné).
BACKUP_DIR ?= db/backups

help:  ## Affiche cette aide
	@echo ""
	@echo "  Novel Realm — commandes Docker"
	@echo "  ------------------------"
	@echo "  make dev          Mode DEV (hot-reload, code monté en volume) — en arrière-plan"
	@echo "  make prod         Mode PROD (images figées) — en arrière-plan"
	@echo "  make down         Arrête et supprime les conteneurs"
	@echo "  make logs         Affiche les logs en continu"
	@echo "  make ps           Liste l'état des conteneurs"
	@echo "  make db           Démarre UNIQUEMENT la base (utile si on lance l'app à la main)"
	@echo "  make restart-api  Redémarre l'API (applique un changement back en dev)"
	@echo "  make rebuild      Reconstruit les images dev sans cache"
	@echo "  make clean        Arrête tout ET supprime les volumes (DONNÉES DB PERDUES)"
	@echo "  make ingest SLUG=<slug> [MAX=50]   Importe un roman LightNovelWorld (one-shot)"
	@echo ""
	@echo "  make migrate      Applique db/migrations/ sur la base (sauvegarde d abord)"
	@echo "  make backup       Sauvegarde la base"
	@echo "  make check-schema Vérifie que db/init/ et db/migrations/ sont d accord"
	@echo "  make backups      Liste les sauvegardes existantes"
	@echo "  make restore FILE=<fichier>   Restaure une sauvegarde (écrase la base)"
	@echo ""
	@echo "  App : http://localhost:5173   API : http://localhost:8080"
	@echo ""

dev:  ## Lance la stack en mode développement (hot-reload), en arrière-plan
	$(COMPOSE) $(DEV) up --build -d
	@echo "Lancé (dev / hot-reload). App : http://localhost:5173   API : http://localhost:8080"
	@echo "Suivre les logs : make logs    |    Arrêter : make down"

prod:  ## Lance la stack en mode production (détaché)
	$(COMPOSE) $(PROD) up --build -d
	@echo ""
	@echo "Lancé. Adresses réelles (celles-ci viennent des conteneurs, pas d'un echo) :"
	@$(COMPOSE) $(PROD) ps --format "  {{.Service}}\t{{.Status}}\t{{.Ports}}"
	@echo ""
	@echo "  Un service absent de cette liste n'a PAS démarré :"
	@echo "    docker compose -p novelrealm logs --tail=40 <service>"

down:  ## Arrête et supprime les conteneurs
	$(COMPOSE) $(DEV) down

logs:  ## Suit les logs de tous les services
	$(COMPOSE) $(DEV) logs -f

ps:  ## État des conteneurs
	$(COMPOSE) $(DEV) ps

db:  ## Démarre seulement PostgreSQL
	$(COMPOSE) up -d postgres

restart-api:  ## Redémarre l'API (applique un changement backend en dev)
	$(COMPOSE) $(DEV) restart api

rebuild:  ## Reconstruit les images dev de zéro (sans cache)
	$(COMPOSE) $(DEV) build --no-cache

clean:  ## Arrête tout et SUPPRIME les volumes (efface la base ET les fichiers !)
	@echo "⚠  Cette commande EFFACE définitivement :"
	@echo "     • toute la base   — comptes, bibliothèques, progression, citations…"
	@echo "     • tous les fichiers téléversés — avatars, bannières"
	@echo ""
	@echo "   Contenu actuel de la base :"
	@$(COMPOSE) exec -T postgres psql -U novelrealm -d novelrealm -tAc \
		"select '     ' || (select count(*) from users) || ' comptes, ' \
		     || (select count(*) from novels) || ' romans, ' \
		     || (select count(*) from passage_annotation) || ' annotations'" \
		2>/dev/null || echo "     (base injoignable — impossible de dire ce qui sera perdu)"
	@echo ""
	@echo "   « make backup » d'abord si le moindre doute."
	@printf "   Taper « effacer » pour continuer : "; \
	read answer; [ "$$answer" = "effacer" ] || { echo "Annulé."; exit 1; }
	$(COMPOSE) $(DEV) down -v

# =====================================================================
#  Sauvegarde / restauration de la base.
#
#  Le réflexe à prendre : `make backup` AVANT d'appliquer une migration
#  sur des données qui comptent. Une migration qui se passe mal se répare
#  en trente secondes quand on a un dump, et pas du tout sinon.
#
#  Format `custom` (-Fc) plutôt que du SQL brut : compressé, et surtout
#  restaurable table par table avec pg_restore si un jour on ne veut
#  récupérer QUE les citations sans écraser le reste.
# =====================================================================

backup:  ## Sauvegarde base + fichiers téléversés dans db/backups/
	@mkdir -p $(BACKUP_DIR)
	@STAMP=$$(date +%Y-%m-%d_%H%M%S); \
	FILE=$(BACKUP_DIR)/novelrealm_$$STAMP.dump; \
	if $(COMPOSE) exec -T postgres pg_dump -U novelrealm -d novelrealm -Fc > $$FILE 2>/dev/null; then \
		echo "Base      : $$FILE ($$(du -h $$FILE | cut -f1))"; \
	else \
		rm -f $$FILE; \
		echo "Échec : la base ne répond pas (« make db » pour la démarrer)."; \
		echo "Aucun fichier créé — une sauvegarde vide serait pire que pas de sauvegarde."; \
		exit 1; \
	fi; \
	TAR=$(BACKUP_DIR)/uploads_$$STAMP.tar.gz; \
	if $(COMPOSE) exec -T api tar -czf - -C /app uploads > $$TAR 2>/dev/null && [ -s $$TAR ]; then \
		echo "Téléversés: $$TAR ($$(du -h $$TAR | cut -f1))"; \
	else \
		rm -f $$TAR; \
		echo "Téléversés: NON sauvegardés — le conteneur api ne tourne pas."; \
		echo "            (avatars et bannières ; relancer avec l'API démarrée)"; \
	fi

# ---------------------------------------------------------------------
#  Applique le contenu de db/migrations/ sur la base EXISTANTE.
#
#  Pourquoi ça peut se rejouer sans risque : chaque migration est écrite en
#  CREATE TABLE / CREATE INDEX / ADD COLUMN « IF NOT EXISTS ». Rejouer une
#  migration déjà passée ne fait donc rien. C'est ce qui dispense d'un outil
#  qui mémorise l'état (Flyway & co) : au lieu de savoir lesquelles ont été
#  appliquées, on les applique TOUTES, à chaque fois, et seules les
#  manquantes ont un effet.
#
#  Corollaire : toute nouvelle migration DOIT être écrite en IF NOT EXISTS,
#  sans quoi cette cible casse au deuxième passage.
#
#  L'ordre vient du tri des noms de fichiers, d'où le préfixe AAAA-MM-JJ.
#
#  Une sauvegarde est prise d'office avant : c'est précisément le moment où
#  on la regrette quand on ne l'a pas.
# ---------------------------------------------------------------------
migrate: backup  ## Applique db/migrations/ sur la base (sauvegarde d'abord)
	@for f in $$(ls db/migrations/*.sql | sort); do \
		printf "  %-42s " "$$(basename $$f)"; \
		OUT=$$($(COMPOSE) exec -T postgres psql -U novelrealm -d novelrealm \
			-v ON_ERROR_STOP=1 -q < $$f 2>&1); \
		if [ $$? -eq 0 ]; then echo "ok"; \
		else echo "ÉCHEC"; echo "$$OUT" | tail -5; exit 1; fi; \
	done
	@echo "Base à jour."

# ---------------------------------------------------------------------
#  Le schéma est décrit à DEUX endroits, et c'est inévitable :
#
#    • db/init/01_init.sql — joué par PostgreSQL une seule fois, à la
#      création du volume. C'est le schéma que reçoit une base NEUVE.
#    • db/migrations/*.sql — joué par « make migrate » sur une base qui
#      existe déjà, et qui ne rejouera JAMAIS l'init.
#
#  Une base neuve et une base en service arrivent donc au même schéma par
#  deux chemins différents. Rien ne garantit qu'ils arrivent au même
#  endroit — sinon la vigilance de celui qui écrit les deux fichiers.
#
#  Cette cible le vérifie : elle monte une base jetable à partir du seul
#  01_init.sql, applique les migrations sur une copie, et compare colonnes
#  et index. Un écart ici veut dire que ta prochaine installation propre
#  n'aura pas le même schéma que ta production.
# ---------------------------------------------------------------------
check-schema:  ## Vérifie que db/init/ et db/migrations/ décrivent le même schéma
	@set -e; \
	PSQL="$(COMPOSE) exec -T postgres psql -U novelrealm -v ON_ERROR_STOP=1 -q"; \
	trap '$$PSQL -d postgres -c "DROP DATABASE IF EXISTS nr_check_init;" >/dev/null 2>&1; \
	      $$PSQL -d postgres -c "DROP DATABASE IF EXISTS nr_check_migr;" >/dev/null 2>&1' EXIT; \
	for db in nr_check_init nr_check_migr; do \
		$$PSQL -d postgres -c "DROP DATABASE IF EXISTS $$db;" >/dev/null; \
		$$PSQL -d postgres -c "CREATE DATABASE $$db;" >/dev/null; \
		$$PSQL -d $$db < db/init/01_init.sql >/dev/null; \
	done; \
	for f in $$(ls db/migrations/*.sql | sort); do \
		$$PSQL -d nr_check_migr < $$f >/dev/null; \
	done; \
	$$PSQL -d nr_check_init -tA < db/schema_snapshot.sql | tr -d '\r' > /tmp/nr_init.txt; \
	$$PSQL -d nr_check_migr -tA < db/schema_snapshot.sql | tr -d '\r' > /tmp/nr_migr.txt; \
	if [ ! -s /tmp/nr_init.txt ] || [ ! -s /tmp/nr_migr.txt ]; then \
		echo "Échec : l'introspection n'a rien renvoyé — vérification NON concluante."; \
		echo "(ne surtout pas lire ça comme « schémas identiques »)"; \
		exit 1; \
	fi; \
	if diff -u /tmp/nr_init.txt /tmp/nr_migr.txt > /tmp/nr_diff.txt; then \
		echo "Schémas identiques — $$(wc -l < /tmp/nr_init.txt) objets comparés."; \
		echo "Une installation neuve aura exactement le schéma d'une base migrée."; \
	else \
		echo "ÉCART entre db/init/01_init.sql et db/migrations/ :"; \
		echo "  « - » = produit par l'init seul   → MANQUE dans db/migrations/"; \
		echo "  « + » = produit par les migrations → MANQUE dans db/init/01_init.sql"; \
		grep -E "^[-+][^-+]" /tmp/nr_diff.txt | sed 's/^/    /'; \
		exit 1; \
	fi

backups:  ## Liste les sauvegardes existantes, de la plus récente à la plus ancienne
	@ls -lht $(BACKUP_DIR)/*.dump 2>/dev/null || echo "Aucune sauvegarde dans $(BACKUP_DIR)/"

restore:  ## Restaure une sauvegarde : make restore FILE=db/backups/<fichier>.dump
	@test -n "$(FILE)" || { echo "Usage : make restore FILE=db/backups/<fichier>.dump"; echo "        (make backups pour voir la liste)"; exit 1; }
	@test -f "$(FILE)" || { echo "Fichier introuvable : $(FILE)"; exit 1; }
	@echo "⚠  Restaurer $(FILE) ÉCRASE la base actuelle — tout ce qui a été créé depuis"
	@echo "   cette sauvegarde sera perdu."
	@printf "   Taper « oui » pour continuer : "; \
	read answer; [ "$$answer" = "oui" ] || { echo "Annulé."; exit 1; }
	@$(COMPOSE) exec -T postgres pg_restore -U novelrealm -d novelrealm \
		--clean --if-exists --no-owner < $(FILE) && echo "Restauré depuis $(FILE)"

ingest:  ## Importe un roman LightNovelWorld (ex: make ingest SLUG=shadow-slave [MAX=100])
	@test -n "$(SLUG)" || { echo "Usage : make ingest SLUG=<slug> [MAX=50]   (slug = .../novel/<slug>/)"; exit 1; }
	@echo "Ingestion one-shot de '$(SLUG)' (max $(MAX) chapitres)…"
	# L'API `dev` tourne déjà et tient les verrous Gradle. Pour coexister sans
	# conflit, l'ingestion utilise des caches Gradle DÉDIÉS (un volume à elle) :
	#   - GRADLE_USER_HOME=/gradle-ingest  → cache global (deps) séparé de /root/.gradle
	#   - --project-cache-dir              → cache PROJET séparé de /app/.gradle (monté)
	# Sans ça : "Timeout waiting to lock ... It is currently in use by another Gradle instance".
	$(COMPOSE) $(DEV) run --rm \
		-e SPRING_PROFILES_ACTIVE=dev,ingest \
		-e NOVELREALM_INGESTION_SLUG=$(SLUG) \
		-e NOVELREALM_INGESTION_MAX_CHAPTERS=$(MAX) \
		-e GRADLE_USER_HOME=/gradle-ingest \
		-v novelrealm_gradle_ingest:/gradle-ingest \
		api ./gradlew bootRun --no-daemon --project-cache-dir /gradle-ingest/project-cache
