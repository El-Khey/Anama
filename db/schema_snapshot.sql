-- Empreinte lisible du schéma courant, utilisée par « make check-schema ».
--
-- Sortie triée, une ligne par objet : deux bases identiques donnent deux
-- fichiers identiques, et « diff » suffit à dire ce qui manque et où.
--
-- Les valeurs par défaut des colonnes BIGSERIAL sont volontairement ignorées :
-- elles contiennent le nom de la séquence, qui varie d'une base à l'autre sans
-- que le schéma diffère pour autant.
SELECT 'colonne  ' || table_name || '.' || column_name
       || ' ' || data_type
       || COALESCE('(' || character_maximum_length || ')', '')
       || CASE WHEN is_nullable = 'YES' THEN ' NULL' ELSE ' NOT NULL' END
  FROM information_schema.columns
 WHERE table_schema = 'public'

UNION ALL

SELECT 'index    ' || tablename || '.' || indexname
  FROM pg_indexes
 WHERE schemaname = 'public'

UNION ALL

-- contype est de type "char" (un octet), pas text : sans le cast explicite,
-- PostgreSQL ne sait pas quel opérateur || choisir et refuse la requête.
SELECT 'contrainte ' || rel.relname || '.' || con.conname || ' ' || con.contype::text
  FROM pg_constraint con
  JOIN pg_class rel ON rel.oid = con.conrelid
  JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
 WHERE nsp.nspname = 'public'

 ORDER BY 1;
