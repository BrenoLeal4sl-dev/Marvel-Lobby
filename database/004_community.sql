-- Marvel Lobby | Comunidade: seguir, seguidores e mensagens diretas
-- pgAdmin -> marvel_mobile -> Query Tool, conectado como avnadmin/migrador.
-- Execute o arquivo inteiro. Preserva contas, biblioteca e conversas do Marvel AI.
-- Pode ser executado novamente: a versão 3 já instalada não é recriada.
BEGIN;
SET LOCAL lock_timeout = '10s';
SET LOCAL search_path = pg_catalog;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM marvel_lobby.schema_migrations WHERE version=2) THEN
        RAISE EXCEPTION 'Instale primeiro a estrutura de identidade online (versão 2).';
    END IF;
    IF EXISTS (SELECT 1 FROM marvel_lobby.schema_migrations WHERE version=3) THEN
        RAISE NOTICE 'Comunidade já instalada; nenhuma tabela foi recriada.';
        RETURN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='marvel_lobby_api')
       OR EXISTS (SELECT 1 FROM pg_roles WHERE rolname='marvel_lobby_api' AND (rolsuper OR rolbypassrls))
       OR pg_has_role('marvel_lobby_api',(SELECT nspowner FROM pg_namespace WHERE nspname='marvel_lobby'),'MEMBER') THEN
        RAISE EXCEPTION 'Configure primeiro o usuário restrito marvel_lobby_api.';
    END IF;

    CREATE TABLE marvel_lobby.follows (
        follower_id uuid NOT NULL REFERENCES marvel_lobby.users(id) ON DELETE CASCADE,
        followed_id uuid NOT NULL REFERENCES marvel_lobby.users(id) ON DELETE CASCADE,
        created_at timestamptz NOT NULL DEFAULT now(),
        PRIMARY KEY(follower_id,followed_id), CHECK(follower_id<>followed_id)
    );
    CREATE INDEX follows_target ON marvel_lobby.follows(followed_id,follower_id);
    ALTER TABLE marvel_lobby.follows ENABLE ROW LEVEL SECURITY;
    CREATE POLICY follows_read ON marvel_lobby.follows FOR SELECT
        USING (marvel_lobby.current_user_id() IS NOT NULL);
    CREATE POLICY follows_add ON marvel_lobby.follows FOR INSERT
        WITH CHECK(follower_id=marvel_lobby.current_user_id());
    CREATE POLICY follows_remove ON marvel_lobby.follows FOR DELETE
        USING(follower_id=marvel_lobby.current_user_id());

    CREATE TABLE marvel_lobby.direct_conversations (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        user_a uuid NOT NULL REFERENCES marvel_lobby.users(id) ON DELETE CASCADE,
        user_b uuid NOT NULL REFERENCES marvel_lobby.users(id) ON DELETE CASCADE,
        created_at timestamptz NOT NULL DEFAULT now(),
        UNIQUE(user_a,user_b), CHECK(user_a<user_b)
    );
    CREATE INDEX direct_conversations_b ON marvel_lobby.direct_conversations(user_b);
    ALTER TABLE marvel_lobby.direct_conversations ENABLE ROW LEVEL SECURITY;
    CREATE POLICY direct_participant_read ON marvel_lobby.direct_conversations FOR SELECT
        USING(marvel_lobby.current_user_id() IN (user_a,user_b));
    CREATE POLICY direct_participant_create ON marvel_lobby.direct_conversations FOR INSERT
        WITH CHECK(marvel_lobby.current_user_id() IN (user_a,user_b));

    CREATE TABLE marvel_lobby.direct_messages (
        id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
        conversation_id uuid NOT NULL REFERENCES marvel_lobby.direct_conversations(id) ON DELETE CASCADE,
        sender_id uuid NOT NULL REFERENCES marvel_lobby.users(id) ON DELETE CASCADE,
        client_id uuid NOT NULL,
        body text NOT NULL CHECK(char_length(btrim(body)) BETWEEN 1 AND 2000),
        sent_at timestamptz NOT NULL DEFAULT now(),
        UNIQUE(sender_id,client_id)
    );
    CREATE INDEX direct_messages_page ON marvel_lobby.direct_messages(conversation_id,id DESC);
    ALTER TABLE marvel_lobby.direct_messages ENABLE ROW LEVEL SECURITY;
    CREATE POLICY direct_message_read ON marvel_lobby.direct_messages FOR SELECT
        USING(EXISTS(SELECT 1 FROM marvel_lobby.direct_conversations c WHERE c.id=conversation_id));
    CREATE POLICY direct_message_send ON marvel_lobby.direct_messages FOR INSERT
        WITH CHECK(sender_id=marvel_lobby.current_user_id()
            AND EXISTS(SELECT 1 FROM marvel_lobby.direct_conversations c WHERE c.id=conversation_id));

    CREATE TABLE marvel_lobby.direct_reads (
        conversation_id uuid NOT NULL REFERENCES marvel_lobby.direct_conversations(id) ON DELETE CASCADE,
        user_id uuid NOT NULL REFERENCES marvel_lobby.users(id) ON DELETE CASCADE,
        last_read_id bigint NOT NULL DEFAULT 0 CHECK(last_read_id>=0),
        PRIMARY KEY(conversation_id,user_id)
    );
    ALTER TABLE marvel_lobby.direct_reads ENABLE ROW LEVEL SECURITY;
    CREATE POLICY direct_receipt_read ON marvel_lobby.direct_reads FOR SELECT
        USING(EXISTS(SELECT 1 FROM marvel_lobby.direct_conversations c WHERE c.id=conversation_id));
    CREATE POLICY direct_receipt_add ON marvel_lobby.direct_reads FOR INSERT
        WITH CHECK(user_id=marvel_lobby.current_user_id()
            AND EXISTS(SELECT 1 FROM marvel_lobby.direct_conversations c WHERE c.id=conversation_id));
    CREATE POLICY direct_receipt_update ON marvel_lobby.direct_reads FOR UPDATE
        USING(user_id=marvel_lobby.current_user_id())
        WITH CHECK(user_id=marvel_lobby.current_user_id()
            AND EXISTS(SELECT 1 FROM marvel_lobby.direct_conversations c WHERE c.id=conversation_id));

    CREATE FUNCTION marvel_lobby.validate_direct_read() RETURNS trigger
    LANGUAGE plpgsql SET search_path=pg_catalog AS $read$
    BEGIN
        IF TG_OP='UPDATE' AND (NEW.conversation_id<>OLD.conversation_id
            OR NEW.user_id<>OLD.user_id OR NEW.last_read_id<OLD.last_read_id) THEN
            RAISE EXCEPTION 'Invalid read receipt' USING ERRCODE='23514';
        END IF;
        IF NEW.last_read_id<>0 AND NOT EXISTS(SELECT 1 FROM marvel_lobby.direct_messages
            WHERE conversation_id=NEW.conversation_id AND id=NEW.last_read_id) THEN
            RAISE EXCEPTION 'Invalid read receipt' USING ERRCODE='23514';
        END IF;
        RETURN NEW;
    END $read$;
    REVOKE ALL ON FUNCTION marvel_lobby.validate_direct_read() FROM PUBLIC;
    CREATE TRIGGER validate_direct_read BEFORE INSERT OR UPDATE ON marvel_lobby.direct_reads
        FOR EACH ROW EXECUTE FUNCTION marvel_lobby.validate_direct_read();

    GRANT SELECT,INSERT,DELETE ON marvel_lobby.follows TO marvel_lobby_api;
    GRANT SELECT,INSERT ON marvel_lobby.direct_conversations,marvel_lobby.direct_messages TO marvel_lobby_api;
    GRANT SELECT,INSERT,UPDATE ON marvel_lobby.direct_reads TO marvel_lobby_api;
    GRANT USAGE ON SEQUENCE marvel_lobby.direct_messages_id_seq TO marvel_lobby_api;
    GRANT EXECUTE ON FUNCTION marvel_lobby.validate_direct_read() TO marvel_lobby_api;
    COMMENT ON TABLE marvel_lobby.follows IS 'Seguir é unilateral; não depende de amizade ou aprovação.';
    COMMENT ON TABLE marvel_lobby.direct_messages IS 'Mensagens entre usuários, separadas do Marvel AI. Texto imutável e envio idempotente por sender/client_id.';
    INSERT INTO marvel_lobby.schema_migrations(version,description)
        VALUES(3,'Comunidade: seguir/seguidores, conversas diretas, mensagens e leitura com RLS');
END $$;
COMMIT;
SELECT version,description,applied_at FROM marvel_lobby.schema_migrations WHERE version=3;
