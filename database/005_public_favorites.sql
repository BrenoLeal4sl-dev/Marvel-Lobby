-- Marvel Lobby | Favoritos no perfil público (versão 4)
-- pgAdmin -> marvel_mobile -> Query Tool, como administrador/migrador.
-- Execute inteiro. Não apaga contas, favoritos locais, histórico ou mensagens.
BEGIN;
SET LOCAL lock_timeout='10s';
SET LOCAL search_path=pg_catalog;
DO $$
BEGIN
    IF NOT EXISTS(SELECT 1 FROM marvel_lobby.schema_migrations WHERE version=3) THEN
        RAISE EXCEPTION 'Execute primeiro 004_community.sql.';
    END IF;
    IF EXISTS(SELECT 1 FROM marvel_lobby.schema_migrations WHERE version=4) THEN
        RAISE NOTICE 'Favoritos públicos já instalados.';
        RETURN;
    END IF;
    IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='marvel_lobby_api')
       OR EXISTS(SELECT 1 FROM pg_roles WHERE rolname='marvel_lobby_api' AND (rolsuper OR rolbypassrls))
       OR pg_has_role('marvel_lobby_api',(SELECT nspowner FROM pg_namespace WHERE nspname='marvel_lobby'),'MEMBER') THEN
        RAISE EXCEPTION 'Configure o usuário restrito marvel_lobby_api.';
    END IF;

    -- Apenas a projeção necessária ao carrossel. Não contém histórico, e-mail ou chats.
    CREATE TABLE marvel_lobby.public_favorites (
        user_id uuid NOT NULL REFERENCES marvel_lobby.users(id) ON DELETE CASCADE,
        resource_type text NOT NULL CHECK(resource_type IN ('character','power','team','story_arc')),
        comic_vine_id integer NOT NULL CHECK(comic_vine_id>0),
        name text NOT NULL CHECK(char_length(btrim(name)) BETWEEN 1 AND 200),
        image_url text CHECK(char_length(image_url)<=2048),
        created_at timestamptz NOT NULL DEFAULT now(),
        PRIMARY KEY(user_id,resource_type,comic_vine_id)
    );
    CREATE INDEX public_favorites_page ON marvel_lobby.public_favorites(user_id,resource_type,created_at DESC,comic_vine_id DESC);
    -- A view expõe somente quem ativou a opção, mantendo preferências privadas com RLS.
    CREATE VIEW marvel_lobby.favorite_sharing_profiles WITH(security_barrier=true) AS
        SELECT p.user_id FROM marvel_lobby.preferences p
        JOIN marvel_lobby.users u ON u.id=p.user_id
        WHERE p.show_favorites AND u.disabled_at IS NULL;
    REVOKE ALL ON marvel_lobby.favorite_sharing_profiles FROM PUBLIC;
    ALTER TABLE marvel_lobby.public_favorites ENABLE ROW LEVEL SECURITY;
    CREATE POLICY public_favorites_read ON marvel_lobby.public_favorites FOR SELECT USING(
        marvel_lobby.current_user_id() IS NOT NULL AND
        (user_id=marvel_lobby.current_user_id() OR EXISTS(
            SELECT 1 FROM marvel_lobby.favorite_sharing_profiles p WHERE p.user_id=public_favorites.user_id)));
    CREATE POLICY public_favorites_write ON marvel_lobby.public_favorites FOR INSERT
        WITH CHECK(user_id=marvel_lobby.current_user_id());
    CREATE POLICY public_favorites_update ON marvel_lobby.public_favorites FOR UPDATE
        USING(user_id=marvel_lobby.current_user_id()) WITH CHECK(user_id=marvel_lobby.current_user_id());
    CREATE POLICY public_favorites_remove ON marvel_lobby.public_favorites FOR DELETE
        USING(user_id=marvel_lobby.current_user_id());
    REVOKE ALL ON marvel_lobby.public_favorites FROM PUBLIC;
    GRANT SELECT,INSERT,UPDATE,DELETE ON marvel_lobby.public_favorites TO marvel_lobby_api;
    GRANT SELECT ON marvel_lobby.favorite_sharing_profiles TO marvel_lobby_api;
    INSERT INTO marvel_lobby.schema_migrations(version,description)
        VALUES(4,'Carrossel de favoritos compartilhados no perfil público');
END $$;
COMMIT;
