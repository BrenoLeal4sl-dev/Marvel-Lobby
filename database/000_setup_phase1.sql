-- MARVEL LOBBY: INSTALACAO INICIAL + FASE 1 SOCIAL
-- Banco novo: abra este arquivo no Query Tool do pgAdmin e execute inteiro.
-- Use a conexao administrativa da Aiven. Nao altere/crie senhas neste SQL.
-- Nao apaga tabelas. Execute apenas uma vez: nao e um script de reset.
-- Se o schema marvel_lobby ja existe, use SOMENTE 003_social_identity.sql.
-- Depois crie o service user marvel_lobby_api na Aiven e execute
-- 002_runtime_permissions.sql com a conexao administrativa do pgAdmin.

-- Marvel Lobby | PostgreSQL 14+ | Aiven / pgAdmin
-- Execute uma vez, no banco escolhido, como administrador/migrador.
-- Não cria banco, usuários de conexão ou senhas. Não altera o schema public.
-- Reexecutar falha sem sobrescrever a estrutura existente; use migrações futuras.
BEGIN;
SET LOCAL lock_timeout = '10s';
SET LOCAL search_path = pg_catalog;

CREATE SCHEMA marvel_lobby;
REVOKE ALL ON SCHEMA marvel_lobby FROM PUBLIC;

CREATE TABLE marvel_lobby.schema_migrations (
    version integer PRIMARY KEY,
    description text NOT NULL,
    applied_at timestamptz NOT NULL DEFAULT now()
);

CREATE FUNCTION marvel_lobby.current_user_id() RETURNS uuid
LANGUAGE sql STABLE SET search_path = pg_catalog
AS $$ SELECT nullif(current_setting('app.user_id', true), '')::uuid $$;

-- Snapshots são os registros reais obtidos da Comic Vine, não dados inventados.
CREATE FUNCTION marvel_lobby.snapshot_matches(payload jsonb, kind text, record_id bigint)
RETURNS boolean LANGUAGE sql IMMUTABLE SET search_path = pg_catalog
AS $$
    SELECT coalesce(jsonb_typeof(payload) = 'object'
        AND payload->>'id' = record_id::text
        AND lower(payload->>'type') = kind
        AND length(btrim(payload->>'name')) > 0, false)
$$;

CREATE FUNCTION marvel_lobby.touch_row() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog
AS $$
BEGIN
    NEW.created_at := OLD.created_at;
    NEW.updated_at := clock_timestamp();
    NEW.version := OLD.version + 1;
    RETURN NEW;
END
$$;

CREATE TABLE marvel_lobby.users (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email text NOT NULL CHECK (length(email) BETWEEN 3 AND 254
        AND email = btrim(email) AND position('@' IN email) > 1
        AND email !~ '[[:space:]]'),
    -- Hash codificado com algoritmo/parâmetros/salt, gerado no backend.
    -- O tamanho não valida criptografia: o backend deve usar uma biblioteca de hashing.
    password_hash text NOT NULL CHECK (length(password_hash) BETWEEN 32 AND 1024),
    password_changed_at timestamptz NOT NULL DEFAULT now(),
    email_verified_at timestamptz,
    disabled_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 1 CHECK (version > 0)
);
CREATE UNIQUE INDEX users_email_unique ON marvel_lobby.users (lower(email));

CREATE TABLE marvel_lobby.profiles (
    user_id uuid PRIMARY KEY REFERENCES marvel_lobby.users(id) ON DELETE CASCADE,
    display_name text NOT NULL CHECK (length(btrim(display_name)) BETWEEN 2 AND 80),
    avatar_url text CHECK (avatar_url IS NULL OR (length(avatar_url) <= 2048 AND avatar_url LIKE 'https://%')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 1 CHECK (version > 0)
);

CREATE TABLE marvel_lobby.preferences (
    user_id uuid PRIMARY KEY REFERENCES marvel_lobby.users(id) ON DELETE CASCADE,
    appearance text NOT NULL DEFAULT 'system' CHECK (appearance IN ('system','light','dark')),
    language text NOT NULL DEFAULT 'pt' CHECK (language IN ('pt','en')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 1 CHECK (version > 0)
);

CREATE TABLE marvel_lobby.sessions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES marvel_lobby.users(id) ON DELETE CASCADE,
    family_id uuid NOT NULL DEFAULT gen_random_uuid(),
    -- SHA-256 de um refresh token aleatório de alta entropia. Nunca guardar o token bruto.
    refresh_token_hash bytea NOT NULL UNIQUE CHECK (octet_length(refresh_token_hash) = 32),
    device_label text CHECK (length(device_label) <= 200),
    created_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL CHECK (expires_at > created_at),
    last_used_at timestamptz,
    revoked_at timestamptz
);
CREATE INDEX sessions_user ON marvel_lobby.sessions(user_id);
CREATE INDEX sessions_family ON marvel_lobby.sessions(user_id, family_id);
CREATE INDEX sessions_expiry ON marvel_lobby.sessions(expires_at);

CREATE TABLE marvel_lobby.catalog_cache (
    resource_type text NOT NULL CHECK (resource_type IN
        ('character','team','power','story_arc','issue','volume','publisher','location')),
    comic_vine_id bigint NOT NULL CHECK (comic_vine_id > 0),
    snapshot jsonb NOT NULL,
    fetched_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL CHECK (expires_at > fetched_at),
    PRIMARY KEY(resource_type, comic_vine_id),
    CHECK (marvel_lobby.snapshot_matches(snapshot,resource_type,comic_vine_id))
);
CREATE INDEX catalog_cache_expiry ON marvel_lobby.catalog_cache(expires_at);

CREATE TABLE marvel_lobby.favorites (
    user_id uuid NOT NULL REFERENCES marvel_lobby.users(id) ON DELETE CASCADE,
    resource_type text NOT NULL CHECK (resource_type IN ('character','team','power','story_arc')),
    comic_vine_id bigint NOT NULL CHECK (comic_vine_id > 0),
    snapshot jsonb NOT NULL,
    -- Remoção lógica para outros dispositivos reconhecerem a exclusão.
    deleted_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 1 CHECK (version > 0),
    PRIMARY KEY(user_id,resource_type,comic_vine_id),
    CHECK (marvel_lobby.snapshot_matches(snapshot,resource_type,comic_vine_id))
);
CREATE INDEX favorites_changes ON marvel_lobby.favorites(user_id,updated_at);
CREATE INDEX favorites_visible ON marvel_lobby.favorites(user_id,resource_type,created_at DESC) WHERE deleted_at IS NULL;

CREATE TABLE marvel_lobby.view_history (
    user_id uuid NOT NULL REFERENCES marvel_lobby.users(id) ON DELETE CASCADE,
    resource_type text NOT NULL CHECK (resource_type IN
        ('character','team','power','story_arc','issue','volume','publisher','location')),
    comic_vine_id bigint NOT NULL CHECK (comic_vine_id > 0),
    snapshot jsonb NOT NULL,
    last_viewed_at timestamptz NOT NULL DEFAULT now(),
    view_count bigint NOT NULL DEFAULT 1 CHECK (view_count > 0),
    deleted_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 1 CHECK (version > 0),
    PRIMARY KEY(user_id,resource_type,comic_vine_id),
    CHECK (marvel_lobby.snapshot_matches(snapshot,resource_type,comic_vine_id))
);
CREATE INDEX history_recent ON marvel_lobby.view_history(user_id,last_viewed_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX history_changes ON marvel_lobby.view_history(user_id,updated_at);

CREATE TABLE marvel_lobby.conversations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES marvel_lobby.users(id) ON DELETE CASCADE,
    title text NOT NULL DEFAULT 'Nova conversa' CHECK (length(btrim(title)) BETWEEN 1 AND 160),
    context_type text CHECK (context_type IN
        ('character','team','power','story_arc','issue','volume','publisher','location')),
    context_id bigint CHECK (context_id > 0),
    context_snapshot jsonb,
    deleted_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 1 CHECK (version > 0),
    UNIQUE(id,user_id),
    CHECK ((context_type IS NULL) = (context_id IS NULL)),
    CHECK (context_snapshot IS NULL OR
        marvel_lobby.snapshot_matches(context_snapshot,context_type,context_id))
);
CREATE INDEX conversations_recent ON marvel_lobby.conversations(user_id,updated_at DESC);

CREATE TABLE marvel_lobby.messages (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id uuid NOT NULL,
    user_id uuid NOT NULL,
    ordinal bigint GENERATED ALWAYS AS IDENTITY,
    role text NOT NULL CHECK (role IN ('user','model')),
    content text NOT NULL CHECK (length(btrim(content)) BETWEEN 1 AND 32000),
    sources jsonb NOT NULL DEFAULT '[]'::jsonb
        CHECK (jsonb_typeof(sources) = 'array' AND jsonb_array_length(sources) <= 6),
    created_at timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY(conversation_id,user_id) REFERENCES marvel_lobby.conversations(id,user_id) ON DELETE CASCADE,
    UNIQUE(conversation_id,ordinal)
);
CREATE INDEX messages_owner ON marvel_lobby.messages(user_id,conversation_id);

-- Invoker functions: nenhuma função assume privilégios de administrador.
DO $$
DECLARE table_name text;
BEGIN
    FOREACH table_name IN ARRAY ARRAY['users','profiles','preferences','favorites','view_history','conversations'] LOOP
        EXECUTE format('CREATE TRIGGER touch_row BEFORE UPDATE ON marvel_lobby.%I FOR EACH ROW EXECUTE FUNCTION marvel_lobby.touch_row()',table_name);
    END LOOP;
    FOREACH table_name IN ARRAY ARRAY['profiles','preferences','favorites','view_history','conversations','messages'] LOOP
        EXECUTE format('ALTER TABLE marvel_lobby.%I ENABLE ROW LEVEL SECURITY',table_name);
        EXECUTE format('CREATE POLICY own_rows ON marvel_lobby.%I USING (user_id = marvel_lobby.current_user_id()) WITH CHECK (user_id = marvel_lobby.current_user_id())',table_name);
    END LOOP;
END
$$;

COMMENT ON TABLE marvel_lobby.users IS 'Somente backend confiável: credenciais de autenticação, sem acesso direto pelo Android.';
COMMENT ON TABLE marvel_lobby.sessions IS 'Somente backend confiável: rotação, expiração e revogação de refresh tokens.';
COMMENT ON TABLE marvel_lobby.favorites IS 'Snapshot independente do cache. deleted_at é uma remoção lógica; version permite detectar conflitos.';
COMMENT ON TABLE marvel_lobby.messages IS 'Mensagens confirmadas. O backend verifica a conversa ativa antes de inserir e serializa envios concorrentes.';
COMMENT ON FUNCTION marvel_lobby.current_user_id() IS 'Definir app.user_id com set_config(..., true) dentro da transação, após autenticar no backend. Não usar UUID recebido sem validação do cliente.';

REVOKE ALL ON ALL TABLES IN SCHEMA marvel_lobby FROM PUBLIC;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA marvel_lobby FROM PUBLIC;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA marvel_lobby FROM PUBLIC;
ALTER DEFAULT PRIVILEGES IN SCHEMA marvel_lobby REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;

INSERT INTO marvel_lobby.schema_migrations(version,description)
VALUES (1,'Estrutura inicial: contas, biblioteca, cache e Marvel AI');
COMMIT;


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
