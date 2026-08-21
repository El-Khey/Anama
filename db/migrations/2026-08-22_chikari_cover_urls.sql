-- =====================================================================
--  Correctif de DONNÉES : les couvertures pointaient vers un site fermé.
--
--  lightnovelworld.org a fermé (fusionné dans chikari.moe, annoncé par
--  ses auteurs). Toutes ses URL d'images répondent désormais « 301 » vers
--  la page d'accueil : le navigateur reçoit du HTML au lieu d'un JPEG, et
--  toutes les couvertures apparaissent brisées, sur tous les appareils à
--  la fois — sans que rien n'ait changé côté NovelRealm.
--
--  Le nouveau CDN CONSERVE le numéro de roman, la conversion est donc
--  mécanique :
--     .../media/covers/batch_0/novel_53.jpg
--     ->  https://cdn.chikari.moe/novels/53/cover.webp
--
--  (Vérifié sur les 7 couvertures concernées : toutes répondent 200, en
--  WebP, plus léger que les JPEG d'origine.)
--
--  Aucune STRUCTURE n'est touchée : ni table, ni colonne, ni index. Une
--  base neuve n'a aucun roman à corriger, donc db/init/01_init.sql n'a
--  rien à refléter et « make check-schema » reste vert.
--
--  Ré-exécutable sans dommage : le WHERE ne retient que l'ANCIENNE forme
--  d'URL. Au second passage il ne trouve plus rien et ne fait rien — la
--  règle qui permet à « make migrate » de tout rejouer à chaque fois.
--
--  ⚠ Ceci ne répare que l'EXISTANT. Les prochaines ingestions ré-écriront
--  des URL mortes tant que le scraper visera l'ancien site : voir
--  LightNovelWorldScraper, dont les sélecteurs ne correspondent plus non
--  plus à la nouvelle page. Et tant que les couvertures sont hotlinkées
--  chez un tiers, le problème peut revenir à son prochain déménagement.
-- =====================================================================

UPDATE novels
SET cover_image_url = 'https://cdn.chikari.moe/novels/'
      || regexp_replace(cover_image_url, '^.*novel_([0-9]+)\.jpg$', '\1')
      || '/cover.webp'
WHERE cover_image_url ~ 'novel_[0-9]+\.jpg$';
