# Comunidade Marvel Lobby

A comunidade segue o pedido mais recente: **seguir/seguidores, bio e chat**, com vínculos unilaterais, sem solicitação de amizade. O catálogo Comic Vine, Gemini e Groq continuam independentes da identidade social.

## Implementado

- Contas online, UUID permanente, @username único, bio e avatar aprovado; associação explícita de contas locais preservando sua biblioteca e chats de IA.
- Home e Perfil abrem Comunidade; a navegação principal mantém os quatro destinos existentes.
- Busca de pessoas por nome ou @username, paginação, perfil público sem e-mail, seguidores/seguindo, seguir/deixar de seguir, acesso à conversa.
- Mensagens diretas entre dois usuários online: conversa única por par, histórico no PostgreSQL, caixa de entrada, não lidas e leitura, paginação do histórico, reenvio idempotente, reconexão em tempo real.
- UI observa CommunityViewModel/StateFlow; Repository separa HTTPS/WebSocket; servidor Fastify usa transações autenticadas e RLS. Chat mantém compositor fora da área rolável e acima do teclado.
- Cards Comic Vine compartilháveis no chat, feed de novos favoritos de pessoas seguidas com opt-in e central de notificações internas.
- Favoritos públicos por categoria e edição de bio. Histórico e conversas de IA têm sincronização privada opcional, fila local persistente, controle de versão e preservação de conversas divergentes.

## Instalação

Banco Aiven `marvel_mobile`: scripts de identidade e permissões existentes mais `database/004_community.sql`, confirmado executado pelo usuário. API em https://marvel-lobby-api.onrender.com; alterações chegam após publicação da revisão do backend. Configuração Android `LOBBY_API_BASE_URL` permanece local, sem credenciais PostgreSQL no aplicativo.

## Limites do escopo

Sem solicitação de amizade, upload de anexos, push do Android ou bloqueio/moderação. Recuperação de conta foi excluída pelo usuário. Mensagens diretas são armazenadas no servidor com acesso limitado aos participantes; não são criptografadas de ponta a ponta. Realtime usa uma instância e reconecta após suspensão da hospedagem gratuita. Rascunhos de mensagens diretas não têm outbox persistente; histórico e chats de IA usam fila persistente para sincronização. Favoritos públicos são uma projeção separada da biblioteca privada.

Scripts adicionais: `005_public_favorites.sql`, `006_social_extensions.sql` e `007_private_sync.sql`. O usuário confirmou os dois últimos em 06/10/2026. Não há envio de e-mail nem credenciais SMTP.

Os testes usam contas sintéticas e PostgreSQL temporário; não criam contas ou mensagens na Aiven. Verificações do banco real leem apenas versões/permissões e validam TLS. A validação final e evidências ficam em IMPLEMENTATION_STATUS.md.
