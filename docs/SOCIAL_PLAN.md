# Marvel Lobby — evolução social

## Diagnóstico e preservação

O Android usa uma Activity com telas nativas, MainViewModel/StateFlow, repositories e coroutines. Room v3 guarda contas locais, biblioteca e chats da IA. DataStore mantém sessão por e-mail/guest, onboarding, idioma e aparência. AccountRepository e MainViewModel gerenciam login/edição; LibraryRepository e ChatRepository usam o dono da sessão como chave. Todos esses dados precisam sobreviver.

Comic Vine fornece catálogo; Gemini fornece chat de IA; Groq fornece traduções. Esses serviços não são a identidade dos usuários Marvel Lobby. As tabelas PostgreSQL conversations/messages existentes são da IA e não serão reutilizadas para conversas entre amigos.

## Tecnologias e arquitetura

Backend Node.js/TypeScript + Fastify + PostgreSQL (Aiven), consultas parametrizadas e RLS. Android acessa endpoints HTTPS; credenciais PostgreSQL ficam somente no backend. Hash de senha scrypt no servidor, sessões opacas com tokens aleatórios/hash SHA-256, expiração/rotação/revogação e limitação de tentativas. Android guarda tokens cifrados usando Keystore. Publicação depende de serviço de hospedagem da API; criar o banco não publica endpoints.

## Modelos e dados

Fase 1: RemoteAccount, PublicUserProfile, Session, LocalAccountBinding. UUID de usuário é permanente, username normalizado é único globalmente, avatar pertence à seleção aprovada, bio é curta e data de entrada vem do servidor. Perfis públicos nunca incluem e-mail/hash/token. Fases seguintes: Friendship/FriendRequest, DirectConversation/DirectMessage, SocialNotification e FriendActivity. IDs Comic Vine aparecem apenas como referências de catálogo.

## Banco

001_initial.sql preservado; 003_social_identity.sql acrescenta username, bio, avatar_id, privacidade e hashes de access tokens. 000_setup_phase1.sql reúne ambas para banco novo. 002_runtime_permissions.sql configura o usuário de serviço, sem superprivilégios. Fase 2 acrescentará amizades com par único de usuários e estados de solicitação. Fase 3 terá tabelas direct_* distintas da IA, membros autorizados, mensagem idempotente e estado de leitura. Fase 4 terá notificações/atividades/referências de conteúdo; nenhum clique comum vira atividade automaticamente.

## Endpoints previstos

Fase 1: POST /v1/auth/register, /login, /refresh, /logout; GET/PATCH /v1/me; PATCH /v1/me/credentials; GET /v1/users/:uuid. Sessão determina o usuário, nunca o userId arbitrário enviado pelo cliente.

Fase 2: busca paginada de pessoas, listar amigos/recebidas/enviadas, enviar/cancelar/aceitar/recusar solicitação e remover amigo. Fase 3: conversas paginadas, mensagens, leitura e eventos por WebSocket autenticado. Fase 4: referências compartilhadas (TEXT/SHARED_CHARACTER/SHARED_TEAM/SHARED_POWER/SHARED_STORY_ARC), favoritos públicos conforme privacidade, atividade e central de notificações. Push somente quando for utilizado.

## Telas e fluxo

Avatar Home continua sendo entrada pessoal; navbar mantém Início/Explorar/Favoritos/Marvel AI. Fase 1 acrescenta Conectar conta online e Perfil público, amplia Meu Perfil/Editar perfil com bio e entrada no app. Conta local → confirmar senha local → criar conta online ou entrar numa conta remota existente → vincular dados locais ao UUID em transação → Meu Perfil. Conflito de username exige escolha; e-mail igual não autoriza vinculação automática. Falha mantém o acesso e dados locais; hashes locais nunca são enviados.

Fase 2: Meu Perfil → Amigos → busca/perfil/solicitações. Fase 3: área pessoal → Mensagens → conversa → perfil do amigo. Fase 4: detalhe Comic Vine → Compartilhar → amigo → conversa → detalhe; Home/área social → atividade; central de notificações → solicitação/perfil/conversa. Badges discretos, vetores, tipografia Jakarta, vermelho e temas existentes. Sem telas vazias simulando recursos online ainda não implementados.

## Ordem e estado

1. Preparar e validar SQL da Fase 1; usuário configura Aiven/service user.
2. Implementar backend/autenticação real e testes de autorização/tokens/perfil.
3. Integrar sessão remota e migração explícita no Android, mantendo acesso local durante transição.
4. Validar preservação de favoritos/histórico/chats, mudança de e-mail e offline.
5. Amizades; depois mensagens em tempo real; depois referências, atividade e notificações.

Estado em 05/10/2026: banco instalado pelo usuário em marvel_mobile/Aiven, TLS e permissões da conta restrita verificados. Backend e integração Android da Fase 1 implementados. Cadastro/login remotos, perfis, rotação de sessões e associação explícita da conta local têm testes próprios. A API iniciou localmente com a Aiven e respondeu /health, sem gravar contas de teste no banco real. O usuário escolheu começar pelo Render gratuito; render.yaml e docs/RENDER_SETUP.md preparam a publicação. Falta hospedar o serviço e configurar a URL HTTPS no Android. Até lá, o app usa acesso local; não apresenta amizades/mensagens como disponíveis.

Referências consultadas: [Fastify](https://fastify.dev/docs/latest/Reference/TypeScript/), [PostgreSQL RLS](https://www.postgresql.org/docs/current/ddl-rowsecurity.html), [node-postgres TLS](https://node-postgres.com/features/ssl), [Node scrypt](https://nodejs.org/api/crypto.html), [armazenamento de senhas OWASP](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html), [Android Keystore](https://developer.android.com/privacy-and-security/keystore).
