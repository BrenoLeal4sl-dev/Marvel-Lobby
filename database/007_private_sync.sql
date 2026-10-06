-- Marvel Lobby | Sincronização privada e opcional (versão 6)
-- Execute inteiro em marvel_mobile como administrador, depois de 006_social_extensions.sql.
BEGIN;
SET LOCAL lock_timeout='10s';
DO $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM marvel_lobby.schema_migrations WHERE version=5) THEN RAISE EXCEPTION 'Execute primeiro 006_social_extensions.sql.'; END IF;
 IF EXISTS(SELECT 1 FROM marvel_lobby.schema_migrations WHERE version=6) THEN RETURN; END IF;
 ALTER TABLE marvel_lobby.preferences ADD COLUMN cloud_sync boolean NOT NULL DEFAULT false;
 CREATE TABLE marvel_lobby.private_archive (
  user_id uuid NOT NULL REFERENCES marvel_lobby.users(id) ON DELETE CASCADE,
  record_key text NOT NULL CHECK(char_length(record_key) BETWEEN 1 AND 100),
  scope text NOT NULL CHECK(scope IN ('history','chat')),
  payload jsonb,
  revision bigint GENERATED ALWAYS AS IDENTITY UNIQUE,
  nonce uuid NOT NULL,updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(user_id,record_key),CHECK(payload IS NULL OR jsonb_typeof(payload)='object')
 );
 CREATE INDEX private_archive_changes ON marvel_lobby.private_archive(user_id,revision);
 ALTER TABLE marvel_lobby.private_archive ENABLE ROW LEVEL SECURITY;
 CREATE POLICY private_archive_owner ON marvel_lobby.private_archive
  USING(user_id=marvel_lobby.current_user_id()) WITH CHECK(user_id=marvel_lobby.current_user_id());
 REVOKE ALL ON marvel_lobby.private_archive FROM PUBLIC;
 GRANT SELECT,INSERT,UPDATE ON marvel_lobby.private_archive TO marvel_lobby_api;
 GRANT USAGE ON SEQUENCE marvel_lobby.private_archive_revision_seq TO marvel_lobby_api;
 INSERT INTO marvel_lobby.schema_migrations(version,description) VALUES(6,'Arquivo privado opcional com controle de versão e remoções sincronizadas');
END $$;
COMMIT;
