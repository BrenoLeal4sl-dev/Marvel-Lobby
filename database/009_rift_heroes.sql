-- Execute inteiro no banco marvel_mobile como administrador.
-- Amplia os personagens aceitos sem apagar partidas ou desafios existentes.
BEGIN;
SET LOCAL lock_timeout='10s';
DO $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM marvel_lobby.schema_migrations WHERE version=7) THEN
  RAISE EXCEPTION 'Execute primeiro 008_rift_arena.sql.';
 END IF;
 IF EXISTS(SELECT 1 FROM marvel_lobby.schema_migrations WHERE version=8) THEN RETURN; END IF;
 ALTER TABLE marvel_lobby.arena_sessions DROP CONSTRAINT arena_sessions_character_key_check;
 ALTER TABLE marvel_lobby.arena_challenges DROP CONSTRAINT arena_challenges_character_key_check;
 ALTER TABLE marvel_lobby.arena_sessions ADD CONSTRAINT arena_sessions_character_key_check
  CHECK(character_key IN ('spider-man','iron-man','hulk','thor','wolverine','doctor-strange'));
 ALTER TABLE marvel_lobby.arena_challenges ADD CONSTRAINT arena_challenges_character_key_check
  CHECK(character_key IN ('spider-man','iron-man','hulk','thor','wolverine','doctor-strange'));
 INSERT INTO marvel_lobby.schema_migrations(version,description) VALUES(8,'Rift Arena: seis personagens jogáveis');
END $$;
COMMIT;
