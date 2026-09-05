-- ============================================================
-- ORACLE DISCOVERY ENGINE
-- Projet : Migration Amplitude RH -> SIRH cible
-- Compatible SQL Developer / SQL*Plus
--
-- IMPORTANT :
--   - Le script n'extrait PAS les données métier.
--   - Il extrait principalement des METADONNEES.
--   - Il travaille sur le périmètre accessible au compte connecté.
--   - Aucun filtre OWNER = 'RH' n'est appliqué.
--
-- Pré-requis :
--   Créer au préalable le dossier :
--       C:/oracle_discovery/
--
-- Exécution :
--   SQL Developer -> ouvrir ce fichier -> F5 (Run Script)
-- ============================================================


-- ============================================================
-- CONFIGURATION
-- ============================================================

DEFINE OUT_DIR = 'C:/oracle_discovery';


-- ============================================================
-- PARAMETRES D'AFFICHAGE
-- ============================================================

SET ECHO OFF
SET FEEDBACK OFF
SET VERIFY OFF
SET HEADING ON
SET PAGESIZE 0
SET LINESIZE 32767
SET TRIMSPOOL ON
SET TAB OFF

SET SQLFORMAT csv


-- ============================================================
-- 00 - INFORMATIONS DE CONNEXION
-- ============================================================

SPOOL &OUT_DIR./00_connection.csv REPLACE

SELECT
    SYS_CONTEXT('USERENV','DB_NAME')             AS db_name,
    SYS_CONTEXT('USERENV','INSTANCE_NAME')      AS instance_name,
    SYS_CONTEXT('USERENV','SERVICE_NAME')       AS service_name,
    SYS_CONTEXT('USERENV','CURRENT_SCHEMA')     AS current_schema,
    SYS_CONTEXT('USERENV','SESSION_USER')       AS session_user,
    SYS_CONTEXT('USERENV','OS_USER')            AS os_user,
    SYS_CONTEXT('USERENV','HOST')               AS host,
    SYS_CONTEXT('USERENV','IP_ADDRESS')         AS ip_address
FROM dual;

SPOOL OFF


-- ============================================================
-- 01 - SCHEMAS / OWNERS ACCESSIBLES
-- ============================================================

SPOOL &OUT_DIR./01_schemas.csv REPLACE

SELECT
    owner,
    COUNT(*) AS object_count
FROM all_objects
GROUP BY owner
ORDER BY object_count DESC, owner;

SPOOL OFF


-- ============================================================
-- 02 - INVENTAIRE DES OBJETS
-- ============================================================

SPOOL &OUT_DIR./02_objects.csv REPLACE

SELECT
    owner,
    object_type,
    COUNT(*) AS object_count
FROM all_objects
GROUP BY owner, object_type
ORDER BY owner, object_type;

SPOOL OFF


-- ============================================================
-- 03 - TABLES
-- ============================================================

SPOOL &OUT_DIR./03_tables.csv REPLACE

SELECT
    owner,
    table_name,
    num_rows,
    tablespace_name,
    partitioned,
    temporary,
    nested,
    last_analyzed
FROM all_tables
ORDER BY owner, table_name;

SPOOL OFF


-- ============================================================
-- 04 - COLONNES
-- ============================================================

SPOOL &OUT_DIR./04_columns.csv REPLACE

SELECT
    owner,
    table_name,
    column_id,
    column_name,
    data_type,
    data_length,
    char_length,
    char_used,
    data_precision,
    data_scale,
    nullable,
    data_default,
    identity_column,
    virtual_column,
    hidden_column
FROM all_tab_columns
ORDER BY owner, table_name, column_id;

SPOOL OFF


-- ============================================================
-- 05 - CONTRAINTES
--
-- P = Primary Key
-- U = Unique
-- C = Check
-- ============================================================

SPOOL &OUT_DIR./05_constraints.csv REPLACE

SELECT
    c.owner,
    c.table_name,
    c.constraint_name,
    c.constraint_type,
    cc.column_name,
    cc.position,
    c.status
FROM all_constraints c
JOIN all_cons_columns cc
    ON c.owner = cc.owner
   AND c.constraint_name = cc.constraint_name
WHERE c.constraint_type IN ('P','U','C')
ORDER BY
    c.owner,
    c.table_name,
    c.constraint_name,
    cc.position;

SPOOL OFF


-- ============================================================
-- 06 - FOREIGN KEYS
--
-- Résultat :
--
-- CHILD
--   |
--   +--> PARENT
--
-- Exemple :
-- RH.EMPLOYE.CONTRAT_ID
--       ->
-- RH.CONTRAT.ID
-- ============================================================

SPOOL &OUT_DIR./06_foreign_keys.csv REPLACE

SELECT
    c.owner              AS child_owner,
    c.table_name         AS child_table,
    cc.column_name      AS child_column,
    cc.position          AS column_position,

    rc.owner             AS parent_owner,
    rc.table_name        AS parent_table,
    rcc.column_name     AS parent_column,

    c.constraint_name    AS foreign_key_name,
    c.status
FROM all_constraints c

JOIN all_cons_columns cc
    ON c.owner = cc.owner
   AND c.constraint_name = cc.constraint_name

JOIN all_constraints rc
    ON c.r_owner = rc.owner
   AND c.r_constraint_name = rc.constraint_name

JOIN all_cons_columns rcc
    ON rc.owner = rcc.owner
   AND rc.constraint_name = rcc.constraint_name
   AND cc.position = rcc.position

WHERE c.constraint_type = 'R'

ORDER BY
    c.owner,
    c.table_name,
    c.constraint_name,
    cc.position;

SPOOL OFF


-- ============================================================
-- 07 - INDEX
-- ============================================================

SPOOL &OUT_DIR./07_indexes.csv REPLACE

SELECT
    owner,
    index_name,
    table_owner,
    table_name,
    uniqueness,
    index_type,
    status,
    tablespace_name,
    num_rows,
    last_analyzed
FROM all_indexes
ORDER BY
    owner,
    table_name,
    index_name;

SPOOL OFF


-- ============================================================
-- 08 - COLONNES DES INDEX
-- ============================================================

SPOOL &OUT_DIR./08_index_columns.csv REPLACE

SELECT
    index_owner,
    index_name,
    table_owner,
    table_name,
    column_position,
    column_name,
    descend
FROM all_ind_columns
ORDER BY
    index_owner,
    table_name,
    index_name,
    column_position;

SPOOL OFF


-- ============================================================
-- 09 - VUES
-- ============================================================

SPOOL &OUT_DIR./09_views.csv REPLACE

SELECT
    owner,
    view_name,
    text_length
FROM all_views
ORDER BY
    owner,
    view_name;

SPOOL OFF


-- ============================================================
-- 10 - SEQUENCES
-- ============================================================

SPOOL &OUT_DIR./10_sequences.csv REPLACE

SELECT
    sequence_owner,
    sequence_name,
    min_value,
    max_value,
    increment_by,
    cycle_flag,
    order_flag,
    cache_size,
    last_number
FROM all_sequences
ORDER BY
    sequence_owner,
    sequence_name;

SPOOL OFF


-- ============================================================
-- 11 - TRIGGERS
--
-- On extrait uniquement les METADONNEES du trigger.
-- Le corps du trigger n'est volontairement pas extrait ici.
-- ============================================================

SPOOL &OUT_DIR./11_triggers.csv REPLACE

SELECT
    owner,
    trigger_name,
    table_owner,
    table_name,
    triggering_event,
    trigger_type,
    status
FROM all_triggers
ORDER BY
    owner,
    table_name,
    trigger_name;

SPOOL OFF


-- ============================================================
-- 12 - PROCEDURES / FONCTIONS / PACKAGES
-- ============================================================

SPOOL &OUT_DIR./12_programs.csv REPLACE

SELECT
    owner,
    object_name,
    procedure_name,
    object_type
FROM all_procedures
ORDER BY
    owner,
    object_name,
    procedure_name;

SPOOL OFF


-- ============================================================
-- 13 - SYNONYMES
-- ============================================================

SPOOL &OUT_DIR./13_synonyms.csv REPLACE

SELECT
    owner,
    synonym_name,
    table_owner,
    table_name
FROM all_synonyms
ORDER BY
    owner,
    synonym_name;

SPOOL OFF


-- ============================================================
-- 14 - DEPENDANCES
-- ============================================================

SPOOL &OUT_DIR./14_dependencies.csv REPLACE

SELECT
    owner,
    name,
    type,
    referenced_owner,
    referenced_name,
    referenced_type
FROM all_dependencies
ORDER BY
    owner,
    name;

SPOOL OFF


-- ============================================================
-- 15 - COMMENTAIRES DES TABLES
-- ============================================================

SPOOL &OUT_DIR./15_table_comments.csv REPLACE

SELECT
    owner,
    table_name,
    comments
FROM all_tab_comments
ORDER BY
    owner,
    table_name;

SPOOL OFF


-- ============================================================
-- 16 - COMMENTAIRES DES COLONNES
-- ============================================================

SPOOL &OUT_DIR./16_column_comments.csv REPLACE

SELECT
    owner,
    table_name,
    column_name,
    comments
FROM all_col_comments
ORDER BY
    owner,
    table_name,
    column_name;

SPOOL OFF


-- ============================================================
-- 17 - OBJETS INVALIDES
-- ============================================================

SPOOL &OUT_DIR./17_invalid_objects.csv REPLACE

SELECT
    owner,
    object_name,
    object_type,
    status,
    created,
    last_ddl_time
FROM all_objects
WHERE status <> 'VALID'
ORDER BY
    owner,
    object_type,
    object_name;

SPOOL OFF


-- ============================================================
-- 18 - COLONNES LOB / TYPES PARTICULIERS
-- ============================================================

SPOOL &OUT_DIR./18_special_columns.csv REPLACE

SELECT
    owner,
    table_name,
    column_name,
    data_type,
    data_length
FROM all_tab_columns
WHERE data_type IN (
    'BLOB',
    'CLOB',
    'NCLOB',
    'LONG',
    'LONG RAW',
    'XMLTYPE'
)
ORDER BY
    owner,
    table_name,
    column_name;

SPOOL OFF


-- ============================================================
-- 19 - TABLES PAR SCHEMA
-- ============================================================

SPOOL &OUT_DIR./19_table_count_by_schema.csv REPLACE

SELECT
    owner,
    COUNT(*) AS table_count
FROM all_tables
GROUP BY owner
ORDER BY
    table_count DESC,
    owner;

SPOOL OFF


-- ============================================================
-- 20 - TABLES SANS PRIMARY KEY
-- ============================================================

SPOOL &OUT_DIR./20_tables_without_pk.csv REPLACE

SELECT
    t.owner,
    t.table_name,
    t.num_rows
FROM all_tables t
WHERE NOT EXISTS (
    SELECT 1
    FROM all_constraints c
    WHERE c.owner = t.owner
      AND c.table_name = t.table_name
      AND c.constraint_type = 'P'
)
ORDER BY
    t.owner,
    t.table_name;

SPOOL OFF


-- ============================================================
-- 21 - PRIVILEGES OBJETS
-- ============================================================

SPOOL &OUT_DIR./21_object_privileges.csv REPLACE

SELECT
    grantee,
    owner,
    table_name,
    privilege,
    grantor
FROM all_tab_privs
ORDER BY
    owner,
    table_name,
    grantee,
    privilege;

SPOOL OFF


-- ============================================================
-- 22 - ROLES DU COMPTE
-- ============================================================

SPOOL &OUT_DIR./22_roles.csv REPLACE

SELECT
    grantee,
    granted_role,
    default_role
FROM user_role_privs
ORDER BY
    granted_role;

SPOOL OFF


-- ============================================================
-- 23 - PRIVILEGES SYSTEME
-- ============================================================

SPOOL &OUT_DIR./23_system_privileges.csv REPLACE

SELECT
    grantee,
    privilege
FROM user_sys_privs
ORDER BY
    privilege;

SPOOL OFF


-- ============================================================
-- 24 - TABLES PARTITIONNEES
-- ============================================================

SPOOL &OUT_DIR./24_partitions.csv REPLACE

SELECT
    table_owner,
    table_name,
    partition_name,
    partition_position,
    tablespace_name
FROM all_tab_partitions
ORDER BY
    table_owner,
    table_name,
    partition_position;

SPOOL OFF


-- ============================================================
-- 25 - RESUME GLOBAL
-- ============================================================

SPOOL &OUT_DIR./25_summary.csv REPLACE

SELECT
    'CURRENT_SCHEMA' AS metric,
    SYS_CONTEXT('USERENV','CURRENT_SCHEMA') AS value
FROM dual

UNION ALL

SELECT
    'SESSION_USER',
    SYS_CONTEXT('USERENV','SESSION_USER')
FROM dual

UNION ALL

SELECT
    'ACCESSIBLE_SCHEMAS',
    TO_CHAR(COUNT(DISTINCT owner))
FROM all_objects

UNION ALL

SELECT
    'ACCESSIBLE_TABLES',
    TO_CHAR(COUNT(*))
FROM all_tables

UNION ALL

SELECT
    'ACCESSIBLE_VIEWS',
    TO_CHAR(COUNT(*))
FROM all_views

UNION ALL

SELECT
    'ACCESSIBLE_SEQUENCES',
    TO_CHAR(COUNT(*))
FROM all_sequences

UNION ALL

SELECT
    'ACCESSIBLE_TRIGGERS',
    TO_CHAR(COUNT(*))
FROM all_triggers

UNION ALL

SELECT
    'ACCESSIBLE_PROCEDURES',
    TO_CHAR(COUNT(*))
FROM all_procedures;

SPOOL OFF


-- ============================================================
-- FIN
-- ============================================================

SET SQLFORMAT ansiconsole
SET FEEDBACK ON

PROMPT
PROMPT ============================================================
PROMPT ORACLE DISCOVERY TERMINEE
PROMPT ============================================================
PROMPT
PROMPT Les fichiers ont ete generes dans :
PROMPT C:/oracle_discovery/
PROMPT
PROMPT Aucun COUNT(*) massif n'a ete execute sur les tables.
PROMPT NUM_ROWS / LAST_ANALYZED ont ete utilises pour la premiere
PROMPT phase de decouverte.
PROMPT ============================================================