-- =====================================================================
--  Nettoyage : romans rangés dans une étagère sans être suivis (issue #44, §3).
--
--  Jusqu'ici, retirer un roman de la bibliothèque supprimait la ligne de
--  « library_entry » mais laissait intacte celle de « category_novel ».
--  Le roman disparaissait donc de l'onglet « Tous » et restait affiché dans
--  son étagère — et il y revenait à chaque rechargement, puisque le serveur
--  le renvoyait toujours.
--
--  Le code est corrigé (LibraryEntryService.remove purge désormais les
--  étagères dans la même transaction). Reste à effacer ce que les anciennes
--  versions ont déjà laissé derrière elles : c'est l'objet de ce fichier.
--
--  À jouer sur une base EXISTANTE :
--      make migrate
--  ou, à la main :
--      docker compose -p novelrealm -f docker-compose.yml \
--          exec -T postgres psql -U novelrealm -d novelrealm \
--          < db/migrations/2026-08-09_orphan_category_novel.sql
--
--  Ré-exécutable sans dommage : la seconde exécution ne trouve plus rien.
--
--  Rien à reporter dans db/init/01_init.sql — cette migration ne touche pas
--  au schéma, elle ne fait que réparer des DONNÉES. Une base neuve part
--  vide, elle n'a donc aucun orphelin à nettoyer.
-- =====================================================================

-- Un rangement n'a de sens que si son propriétaire suit le roman. On compare
-- donc sur le COUPLE (utilisateur de l'étagère, roman) : un roman peut très
-- bien être suivi par quelqu'un d'autre, ce qui ne légitime en rien sa
-- présence dans MON étagère.
DELETE FROM category_novel cn
USING categories c
WHERE cn.category_id = c.id
  AND NOT EXISTS (
      SELECT 1
      FROM library_entry le
      WHERE le.user_id = c.user_id
        AND le.novel_id = cn.novel_id
  );
