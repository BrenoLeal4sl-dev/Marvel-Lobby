-- Marvel Lobby | Fase 1 social | PostgreSQL 14+
-- Execute uma vez depois de 001_initial.sql, com o mesmo administrador/migrador.
-- Não apaga contas, biblioteca ou conversas da IA. Não cria usuários de conexão.
BEGIN;
SET LOCAL lock_timeout = '10s';
SET LOCAL search_path = pg_catalog;

ALTER TABLE marvel_lobby.profiles ADD COLUMN username text;
-- Compatibilidade com possíveis contas já existentes no backend.
-- O Android enviará o username escolhido ao cadastrar novas contas.
UPDATE marvel_lobby.profiles
SET username = 'hero_' || right(replace(user_id::text, '-', ''), 19);
ALTER TABLE marvel_lobby.profiles ALTER COLUMN username SET NOT NULL;
ALTER TABLE marvel_lobby.profiles ADD CONSTRAINT profiles_username_format CHECK (
    username = lower(username)
    AND username ~ '^[a-z0-9_]{3,24}$'
    AND username ~ '[a-z0-9]'
);
CREATE UNIQUE INDEX profiles_username_unique ON marvel_lobby.profiles(username);

ALTER TABLE marvel_lobby.profiles ADD COLUMN bio text NOT NULL DEFAULT ''
    CHECK (char_length(bio) <= 280);
-- Identificadores da seleção aprovada de avatares já incluída no Android.
-- Não aceitar file://, content:// ou uma URL arbitrária enviada pelo cliente.
ALTER TABLE marvel_lobby.profiles ADD COLUMN avatar_id bigint
    CHECK (avatar_id IN (1440,1443,1455,1442,2268,2267,1444,3200));

-- Defaults privados. Estas opções serão usadas pela Fase 4.
ALTER TABLE marvel_lobby.preferences ADD COLUMN share_activity boolean NOT NULL DEFAULT false;
ALTER TABLE marvel_lobby.preferences ADD COLUMN show_favorites boolean NOT NULL DEFAULT false;

-- Tokens opacos aleatórios; somente hashes SHA-256 de 32 bytes no servidor.
-- Refresh e access tokens brutos jamais ficam nestas tabelas.
ALTER TABLE marvel_lobby.sessions ADD COLUMN access_token_hash bytea;
ALTER TABLE marvel_lobby.sessions ADD COLUMN access_expires_at timestamptz;
ALTER TABLE marvel_lobby.sessions ADD CONSTRAINT sessions_access_token_valid CHECK (
    (access_token_hash IS NULL AND access_expires_at IS NULL)
    OR (access_token_hash IS NOT NULL AND access_expires_at IS NOT NULL
        AND octet_length(access_token_hash) = 32
        AND access_expires_at > created_at
        AND access_expires_at <= expires_at)
);
CREATE UNIQUE INDEX sessions_access_token_unique ON marvel_lobby.sessions(access_token_hash)
    WHERE access_token_hash IS NOT NULL;
-- Sessões de uma implementação anterior não ganham tokens de acesso fictícios.
UPDATE marvel_lobby.sessions SET revoked_at = coalesce(revoked_at, now())
WHERE access_token_hash IS NULL;

-- A view é somente leitura e expõe explicitamente os campos públicos.
-- O dono da view é o administrador/migrador, nunca marvel_lobby_api.
-- As tabelas privadas continuam com RLS own_rows: nenhum e-mail, hash,
-- preferência, favorito, histórico ou conversa é exposto por esta view.
CREATE VIEW marvel_lobby.public_profiles WITH (security_barrier=true) AS
SELECT p.user_id AS id, p.display_name, p.username, p.bio, p.avatar_id,
       u.created_at AS joined_at
FROM marvel_lobby.profiles p
JOIN marvel_lobby.users u ON u.id = p.user_id
WHERE u.disabled_at IS NULL;
REVOKE ALL ON marvel_lobby.public_profiles FROM PUBLIC;

COMMENT ON COLUMN marvel_lobby.profiles.username IS 'Único globalmente; normalizado pelo backend sem @. UUID do usuário é a identidade interna permanente.';
COMMENT ON COLUMN marvel_lobby.profiles.avatar_id IS 'ID do avatar aprovado; não representa um usuário e não autoriza upload de arquivos.';
COMMENT ON VIEW marvel_lobby.public_profiles IS 'Somente perfil público. Backend exige autenticação e controla busca/paginação/rate limit. Sem dados da conta, biblioteca privada ou chats.';
COMMENT ON TABLE marvel_lobby.conversations IS 'Conversas privadas com Marvel AI. Futuras conversas entre amigos terão tabelas próprias.';
COMMENT ON TABLE marvel_lobby.messages IS 'Mensagens do Marvel AI (user/model). Não reutilizar para mensagens diretas entre usuários.';
COMMENT ON COLUMN marvel_lobby.sessions.access_token_hash IS 'Hash de token opaco; verificar expiração, revogação e disabled_at a cada autenticação.';

-- Compatível com bancos que já receberam o script de permissões.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname='marvel_lobby_api') THEN
        IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname='marvel_lobby_api' AND (rolsuper OR rolbypassrls))
           OR pg_has_role('marvel_lobby_api', (SELECT nspowner FROM pg_catalog.pg_namespace WHERE nspname='marvel_lobby'), 'MEMBER') THEN
            RAISE EXCEPTION 'O usuário da API não pode ser administrador nem membro do dono do schema.';
        END IF;
        GRANT SELECT ON marvel_lobby.public_profiles,marvel_lobby.schema_migrations TO marvel_lobby_api;
    END IF;
END
$$;

INSERT INTO marvel_lobby.schema_migrations(version,description)
VALUES (2,'Fase 1 social: username global, bio, avatar aprovado, privacidade e tokens de acesso');
COMMIT;
