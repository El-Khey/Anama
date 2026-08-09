-- =====================================================================
--  Nouveau jeu de réactions de passage (issue #44, §8).
--
--  Le jeu d'origine — ❤️ 😂 😮 😢 🔥 💀 — était celui des réseaux sociaux
--  généralistes. Il lui manquait les deux réactions propres au roman : le
--  RETOURNEMENT et le FRISSON. Nouveau jeu :
--
--      ❤️ j'adore · 😂 drôle · 🤯 le retournement
--      😭 ça m'a brisé · 🔥 ça envoie · 😱 glaçant
--
--  ⚠️ POURQUOI CE FICHIER EST OBLIGATOIRE, et pas un simple confort :
--  le back ne renvoie les compteurs que pour les emojis du jeu courant
--  (PassageSocialService.ALLOWED_EMOJIS). Déployer le nouveau jeu sans
--  réécrire les réactions déjà posées les ferait donc DISPARAÎTRE de
--  l'affichage — silencieusement, alors que les lignes sont toujours en
--  base. À jouer AVANT ou AVEC la mise en service du nouveau code.
--
--  À jouer sur une base EXISTANTE :
--      make migrate
--
--  Ré-exécutable sans dommage : la seconde exécution ne trouve plus
--  d'ancien emoji à réécrire.
--
--  Rien à reporter dans db/init/01_init.sql : le jeu d'emojis n'est pas
--  dans le schéma (la colonne est un simple VARCHAR(16)), il vit dans le
--  code. Une base neuve n'a aucune réaction à convertir.
-- =====================================================================

-- Chaque ancien emoji rejoint son plus proche voisin du nouveau jeu. Aucune
-- fusion : deux anciens ne tombent jamais sur le même nouveau, donc aucun
-- compteur n'en absorbe un autre.
--
--     😮 surprise      → 🤯 le retournement
--     😢 triste        → 😭 ça m'a brisé
--     💀 le choc       → 😱 glaçant
--
-- Si tu lis plutôt 💀 comme « mort de rire », remplace 😱 par 😂 dans la
-- ligne correspondante AVANT de jouer ce fichier. C'est sans risque de
-- doublon : l'unicité porte sur (user_id, chapter_id, text_hash), pas sur
-- l'emoji — un lecteur n'a de toute façon qu'une réaction par passage.
UPDATE passage_annotation SET emoji = '🤯' WHERE kind = 'REACTION' AND emoji = '😮';
UPDATE passage_annotation SET emoji = '😭' WHERE kind = 'REACTION' AND emoji = '😢';
UPDATE passage_annotation SET emoji = '😱' WHERE kind = 'REACTION' AND emoji = '💀';

-- Contrôle : plus aucune réaction ne doit porter un emoji hors du jeu.
-- Un résultat non vide signale des réactions que l'app n'affichera plus.
DO $$
DECLARE
    restantes BIGINT;
BEGIN
    SELECT count(*) INTO restantes
    FROM passage_annotation
    WHERE kind = 'REACTION'
      AND emoji IS NOT NULL
      AND emoji NOT IN ('❤️', '😂', '🤯', '😭', '🔥', '😱');

    IF restantes > 0 THEN
        RAISE WARNING 'Réactions portant un emoji hors du jeu courant : %', restantes;
    END IF;
END
$$;
