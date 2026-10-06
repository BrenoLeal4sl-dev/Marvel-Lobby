-- Marvel Lobby | Cards no chat, atividades e central de notificações (versão 5)
-- Execute inteiro no Query Tool de marvel_mobile como administrador.
-- Não remove contas ou mensagens. Atividades ficam privadas por padrão.
BEGIN;
SET LOCAL lock_timeout='10s';
DO $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM marvel_lobby.schema_migrations WHERE version=4) THEN
  RAISE EXCEPTION 'Execute primeiro 005_public_favorites.sql.';
 END IF;
 IF EXISTS(SELECT 1 FROM marvel_lobby.schema_migrations WHERE version=5) THEN RETURN; END IF;
 ALTER TABLE marvel_lobby.direct_messages ADD COLUMN shared_content jsonb;
 ALTER TABLE marvel_lobby.direct_messages ADD CONSTRAINT shared_content_shape CHECK(
  shared_content IS NULL OR (jsonb_typeof(shared_content)='object'
   AND shared_content->>'type' IN ('character','team','power','story_arc')
   AND jsonb_typeof(shared_content->'id')='number'
   AND (shared_content->>'id')::numeric BETWEEN 1 AND 2147483647
   AND char_length(shared_content->>'name') BETWEEN 1 AND 200));
 CREATE TABLE marvel_lobby.activity (
  user_id uuid NOT NULL REFERENCES marvel_lobby.users(id) ON DELETE CASCADE,
  resource_type text NOT NULL CHECK(resource_type IN ('character','team','power','story_arc')),
  comic_vine_id integer NOT NULL CHECK(comic_vine_id>0),
  name text NOT NULL CHECK(char_length(name) BETWEEN 1 AND 200),image_url text,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(user_id,resource_type,comic_vine_id)
 );
 CREATE INDEX activity_recent ON marvel_lobby.activity(created_at DESC);
 CREATE VIEW marvel_lobby.activity_sharing_profiles WITH(security_barrier=true) AS
  SELECT p.user_id FROM marvel_lobby.preferences p JOIN marvel_lobby.users u ON u.id=p.user_id
  WHERE p.share_activity AND u.disabled_at IS NULL;
 ALTER TABLE marvel_lobby.activity ENABLE ROW LEVEL SECURITY;
 CREATE POLICY activity_read ON marvel_lobby.activity FOR SELECT USING(
  user_id=marvel_lobby.current_user_id() OR (EXISTS(SELECT 1 FROM marvel_lobby.activity_sharing_profiles p WHERE p.user_id=activity.user_id)
   AND EXISTS(SELECT 1 FROM marvel_lobby.follows f WHERE f.follower_id=marvel_lobby.current_user_id() AND f.followed_id=activity.user_id)));
 CREATE POLICY activity_insert ON marvel_lobby.activity FOR INSERT WITH CHECK(user_id=marvel_lobby.current_user_id());
 CREATE POLICY activity_delete ON marvel_lobby.activity FOR DELETE USING(user_id=marvel_lobby.current_user_id());
 CREATE TABLE marvel_lobby.notification_reads (
  user_id uuid NOT NULL REFERENCES marvel_lobby.users(id) ON DELETE CASCADE,
  event_key text NOT NULL CHECK(char_length(event_key) BETWEEN 1 AND 160),
  read_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(user_id,event_key)
 );
 ALTER TABLE marvel_lobby.notification_reads ENABLE ROW LEVEL SECURITY;
 CREATE POLICY notification_owner ON marvel_lobby.notification_reads
  USING(user_id=marvel_lobby.current_user_id()) WITH CHECK(user_id=marvel_lobby.current_user_id());
 REVOKE ALL ON marvel_lobby.activity,marvel_lobby.notification_reads,marvel_lobby.activity_sharing_profiles FROM PUBLIC;
 GRANT SELECT,INSERT,DELETE ON marvel_lobby.activity TO marvel_lobby_api;
 GRANT SELECT,INSERT ON marvel_lobby.notification_reads TO marvel_lobby_api;
 GRANT SELECT ON marvel_lobby.activity_sharing_profiles TO marvel_lobby_api;
 INSERT INTO marvel_lobby.schema_migrations(version,description) VALUES(5,'Cards no chat, atividades com privacidade e notificações internas');
END $$;
COMMIT;
