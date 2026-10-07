-- Rift Arena | versão 7. Execute inteiro em marvel_mobile como administrador.
-- Requer 007_private_sync.sql. Não apaga dados existentes.
BEGIN;
SET LOCAL lock_timeout='10s';
DO $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM marvel_lobby.schema_migrations WHERE version=6) THEN RAISE EXCEPTION 'Execute primeiro 007_private_sync.sql.'; END IF;
 IF EXISTS(SELECT 1 FROM marvel_lobby.schema_migrations WHERE version=7) THEN RETURN; END IF;
 CREATE TABLE marvel_lobby.arena_challenges (
  id uuid PRIMARY KEY,challenger_id uuid NOT NULL REFERENCES marvel_lobby.users(id) ON DELETE CASCADE,
  challenged_id uuid NOT NULL REFERENCES marvel_lobby.users(id) ON DELETE CASCADE,
  client_id uuid NOT NULL,seed integer NOT NULL,character_key text NOT NULL CHECK(character_key='spider-man'),
  game_version text NOT NULL,created_at timestamptz NOT NULL DEFAULT now(),expires_at timestamptz NOT NULL,
  UNIQUE(challenger_id,client_id),CHECK(challenger_id<>challenged_id)
 );
 CREATE TABLE marvel_lobby.arena_sessions (
  id uuid PRIMARY KEY,user_id uuid NOT NULL REFERENCES marvel_lobby.users(id) ON DELETE CASCADE,
  client_id uuid NOT NULL,seed integer NOT NULL,character_key text NOT NULL CHECK(character_key='spider-man'),
  game_version text NOT NULL,challenge_id uuid REFERENCES marvel_lobby.arena_challenges(id) ON DELETE SET NULL,
  started_at timestamptz NOT NULL DEFAULT now(),expires_at timestamptz NOT NULL,abandoned_at timestamptz,
  UNIQUE(user_id,client_id),UNIQUE(id,user_id)
 );
 CREATE UNIQUE INDEX arena_challenge_attempt ON marvel_lobby.arena_sessions(user_id,challenge_id) WHERE challenge_id IS NOT NULL;
 CREATE TABLE marvel_lobby.arena_runs (
  session_id uuid PRIMARY KEY,user_id uuid NOT NULL REFERENCES marvel_lobby.users(id) ON DELETE CASCADE,
  character_key text NOT NULL,game_version text NOT NULL,score integer NOT NULL CHECK(score>=0),
  duration real NOT NULL CHECK(duration BETWEEN 0 AND 600.1),kills integer NOT NULL CHECK(kills>=0),
  elites integer NOT NULL CHECK(elites>=0),bosses integer NOT NULL CHECK(bosses BETWEEN 0 AND 5),
  damage real NOT NULL CHECK(damage>=0),max_combo integer NOT NULL CHECK(max_combo>=0),
  level integer NOT NULL CHECK(level>=1),extracted boolean NOT NULL,upgrades jsonb NOT NULL,
  submitted_at timestamptz NOT NULL DEFAULT now(),
  FOREIGN KEY(session_id,user_id) REFERENCES marvel_lobby.arena_sessions(id,user_id) ON DELETE CASCADE
 );
 CREATE INDEX arena_rank ON marvel_lobby.arena_runs(game_version,score DESC,submitted_at,user_id);
 CREATE INDEX arena_user_history ON marvel_lobby.arena_runs(user_id,submitted_at DESC);
 ALTER TABLE marvel_lobby.arena_sessions ENABLE ROW LEVEL SECURITY;
 ALTER TABLE marvel_lobby.arena_runs ENABLE ROW LEVEL SECURITY;
 ALTER TABLE marvel_lobby.arena_challenges ENABLE ROW LEVEL SECURITY;
 CREATE POLICY arena_sessions_owner ON marvel_lobby.arena_sessions USING(user_id=marvel_lobby.current_user_id()) WITH CHECK(user_id=marvel_lobby.current_user_id());
 CREATE POLICY arena_runs_owner ON marvel_lobby.arena_runs USING(user_id=marvel_lobby.current_user_id()) WITH CHECK(user_id=marvel_lobby.current_user_id());
 CREATE POLICY arena_challenges_read ON marvel_lobby.arena_challenges FOR SELECT USING(marvel_lobby.current_user_id() IN(challenger_id,challenged_id));
 CREATE POLICY arena_challenges_insert ON marvel_lobby.arena_challenges FOR INSERT WITH CHECK(challenger_id=marvel_lobby.current_user_id());
 -- Deliberately limited public competitive projection. Session seeds/nonces and private data are excluded.
 CREATE VIEW marvel_lobby.arena_public_runs WITH(security_barrier=true) AS
  SELECT r.session_id,r.user_id,r.character_key,r.game_version,r.score,r.duration,r.kills,r.elites,r.bosses,r.max_combo,r.level,r.extracted,r.submitted_at,s.challenge_id
  FROM marvel_lobby.arena_runs r JOIN marvel_lobby.arena_sessions s ON s.id=r.session_id JOIN marvel_lobby.public_profiles p ON p.id=r.user_id;
 ALTER TABLE marvel_lobby.direct_messages ADD COLUMN rift_content jsonb CHECK(rift_content IS NULL OR jsonb_typeof(rift_content)='object');
 CREATE TABLE marvel_lobby.arena_achievements (
  user_id uuid NOT NULL REFERENCES marvel_lobby.users(id) ON DELETE CASCADE,
  session_id uuid NOT NULL REFERENCES marvel_lobby.arena_runs(session_id) ON DELETE CASCADE,
  kind text NOT NULL CHECK(kind IN('record','first_boss')),score integer NOT NULL CHECK(score>=0),
  created_at timestamptz NOT NULL DEFAULT now(),PRIMARY KEY(user_id,session_id,kind)
 );
 ALTER TABLE marvel_lobby.arena_achievements ENABLE ROW LEVEL SECURITY;
 CREATE POLICY arena_achievements_read ON marvel_lobby.arena_achievements FOR SELECT USING(
  user_id=marvel_lobby.current_user_id() OR (EXISTS(SELECT 1 FROM marvel_lobby.activity_sharing_profiles p WHERE p.user_id=arena_achievements.user_id)
   AND EXISTS(SELECT 1 FROM marvel_lobby.follows f WHERE f.follower_id=marvel_lobby.current_user_id() AND f.followed_id=arena_achievements.user_id)));
 CREATE POLICY arena_achievements_insert ON marvel_lobby.arena_achievements FOR INSERT WITH CHECK(user_id=marvel_lobby.current_user_id());
 CREATE POLICY arena_achievements_delete ON marvel_lobby.arena_achievements FOR DELETE USING(user_id=marvel_lobby.current_user_id());
 REVOKE ALL ON marvel_lobby.arena_achievements FROM PUBLIC;
 GRANT SELECT,INSERT,DELETE ON marvel_lobby.arena_achievements TO marvel_lobby_api;
 REVOKE ALL ON marvel_lobby.arena_sessions,marvel_lobby.arena_runs,marvel_lobby.arena_challenges,marvel_lobby.arena_public_runs FROM PUBLIC;
 GRANT SELECT,INSERT ON marvel_lobby.arena_sessions,marvel_lobby.arena_runs,marvel_lobby.arena_challenges TO marvel_lobby_api;
 GRANT UPDATE(abandoned_at) ON marvel_lobby.arena_sessions TO marvel_lobby_api;
 GRANT SELECT ON marvel_lobby.arena_public_runs TO marvel_lobby_api;
 INSERT INTO marvel_lobby.schema_migrations(version,description) VALUES(7,'Rift Arena: sessões, resultados públicos validados e desafios privados');
END $$;
COMMIT;
