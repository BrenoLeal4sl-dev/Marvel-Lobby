# Comunidade Marvel Lobby

A comunidade segue o pedido mais recente: **seguir/seguidores, bio e chat**, com vínculos unilaterais, sem solicitação de amizade. O catálogo Comic Vine, Gemini e Groq continuam independentes da identidade social.

## Implementado

- Contas online, UUID permanente, @username único, bio e avatar aprovado; associação explícita de contas locais preservando sua biblioteca e chats de IA.
- Home e Perfil abrem Comunidade; a navegação principal mantém os quatro destinos existentes.
- Busca de pessoas por nome ou @username, paginação, perfil público sem e-mail, seguidores/seguindo, seguir/deixar de seguir, acesso à conversa.
- Mensagens diretas entre dois usuários online: conversa única por par, histórico no PostgreSQL, caixa de entrada, não lidas e leitura, paginação do histórico, reenvio idempotente, reconexão em tempo real.
- UI observa CommunityViewModel/StateFlow; Repository separa HTTPS/WebSocket; servidor Fastify usa transações autenticadas e RLS. Chat mantém compositor fora da área rolável e acima do teclado.

## Instalação

Banco Aiven `marvel_mobile`: scripts de identidade e permissões existentes mais `database/004_community.sql`, confirmado executado pelo usuário. API em https://marvel-lobby-api.onrender.com; alterações chegam após publicação da revisão do backend. Configuração Android `LOBBY_API_BASE_URL` permanece local, sem credenciais PostgreSQL no aplicativo.

## Limites do escopo

Sem solicitação de amizade, feed, compartilhamento de registros, anexos, push, bloqueio/moderação ou sincronização de biblioteca. Chats de IA continuam locais. Mensagens diretas são armazenadas no servidor com acesso limitado aos participantes; não são criptografadas de ponta a ponta. Realtime usa uma instância e reconecta após suspensão da hospedagem gratuita. Rascunhos não têm outbox persistente.

Os testes usam contas sintéticas e PostgreSQL temporário; não criam contas ou mensagens na Aiven. Verificações do banco real leem apenas versões/permissões e validam TLS. A validação final e evidências ficam em IMPLEMENTATION_STATUS.md.
