-- Execute depois de criar um usuário de serviço chamado marvel_lobby_api na Aiven.
-- A senha fica na Aiven / .env, nunca neste arquivo.
-- Execute como o mesmo administrador/migrador que executou 001_initial.sql.
BEGIN;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'marvel_lobby_api') THEN
        RAISE EXCEPTION 'Crie primeiro o usuário de serviço marvel_lobby_api na Aiven.';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'marvel_lobby_api' AND (rolsuper OR rolbypassrls))
        OR pg_has_role('marvel_lobby_api', (SELECT nspowner FROM pg_catalog.pg_namespace WHERE nspname='marvel_lobby'), 'MEMBER') THEN
        RAISE EXCEPTION 'O usuário do backend não pode ser administrador, BYPASSRLS ou membro do dono do schema.';
    END IF;
END
$$;

GRANT USAGE ON SCHEMA marvel_lobby TO marvel_lobby_api;
GRANT SELECT,INSERT,UPDATE,DELETE ON marvel_lobby.users,marvel_lobby.sessions,marvel_lobby.catalog_cache TO marvel_lobby_api;
GRANT SELECT,INSERT,UPDATE ON marvel_lobby.profiles,marvel_lobby.preferences,
    marvel_lobby.favorites,marvel_lobby.view_history,marvel_lobby.conversations TO marvel_lobby_api;
GRANT SELECT,INSERT ON marvel_lobby.messages TO marvel_lobby_api;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA marvel_lobby TO marvel_lobby_api;
GRANT EXECUTE ON FUNCTION marvel_lobby.current_user_id(),
    marvel_lobby.snapshot_matches(jsonb,text,bigint),marvel_lobby.touch_row() TO marvel_lobby_api;
GRANT SELECT ON marvel_lobby.schema_migrations TO marvel_lobby_api;
DO $$
BEGIN
    IF to_regclass('marvel_lobby.public_profiles') IS NOT NULL THEN
        GRANT SELECT ON marvel_lobby.public_profiles TO marvel_lobby_api;
    END IF;
END
$$;
COMMIT;
