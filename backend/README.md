# API do Marvel Lobby — identidade e comunidade

Este serviço é a ponte HTTPS entre o Android e o PostgreSQL da Aiven. O celular nunca recebe usuário/senha do PostgreSQL. Inclui cadastro, login, sessões, perfil público, bio, seguir/seguidores, mensagens privadas e favoritos publicados por escolha do usuário. Solicitações de amizade, push e sincronização completa da biblioteca entre dispositivos não fazem parte deste escopo. O catálogo Comic Vine, o chat Gemini e a tradução Groq continuam separados.

## Bio e favoritos públicos

Execute `database/005_public_favorites.sql` no banco `marvel_mobile`, como administrador, depois da migração de comunidade. O script instala a versão 4 e concede acesso ao usuário restrito da API; pode ser executado novamente sem apagar dados.

- `PATCH /v1/me/bio`: atualiza somente a bio, até 280 caracteres Unicode; não exige mudar senha, nome, username ou avatar.
- `GET` / `PUT /v1/community/me/favorite-sharing`: consulta ou altera a visibilidade. Começa privada.
- `POST /v1/community/me/favorites`: aplica até 20 alterações por lote. Recebe tipo, ID Comic Vine, nome, imagem e estado de favorito. O dono vem da sessão, nunca do corpo da requisição.
- `GET /v1/community/users/:id/favorites?type=character&offset=0&limit=12`: página por categoria (`character`, `power`, `team`, `story_arc`); retorna `visible`, `items`, `next`. Perfil privado não retorna itens nem contagens.

O Android mantém favoritos no Room e uma fila durável de publicação/remoção. Uploads são repetíveis; uma confirmação antiga não remove uma alteração local mais recente. Ativar a visibilidade publica a biblioteca existente antes de exibi-la. Ocultar altera a privacidade antes de qualquer upload. Falhas preservam os favoritos locais e a fila; a sincronização retoma ao entrar no app, reconectar ou tentar novamente. Histórico, descrições completas e chats de IA não são publicados. Somente imagens HTTPS dos hosts Comic Vine aprovados são retornadas. A política RLS permite leitura pública apenas quando a opção está ativada e mantém escrita restrita ao próprio usuário.

## Rodar no computador

Requer Node.js 22 ou superior e o banco instalado com os scripts em `../database`. Use a conta restrita `marvel_lobby_api`, com as permissões de `002_runtime_permissions.sql` no banco **marvel_mobile**.

```powershell
cd backend
npm ci
npm run build
npm run check-db
npm start
```

O servidor lê `database/.env` independentemente do diretório atual; `BACKEND_ENV_FILE` permite outro arquivo. Variáveis de ambiente têm prioridade. Nunca publique esse arquivo ou coloque as credenciais no Android. `check-db` apenas lê informações do banco: valida TLS/certificado, função restrita, versão do esquema e leitura do perfil público. O servidor verifica isso também antes de aceitar conexões.

Configuração: `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`, `PGSSLMODE=verify-full`, `PGSSLROOTCERT`. O certificado CA em `database/secrets/ca.pem` é obrigatório; a conexão verifica certificado e hostname. O pool usa no máximo cinco conexões, dentro do limite atual de 20. Não execute a API com `avnadmin`.

O padrão é `http://127.0.0.1:4100` para desenvolvimento local. `/health` responde o estado do processo. `HOST` e `PORT` podem ser definidos na hospedagem. Para o Android, é obrigatório um endereço **HTTPS** público com certificado válido; uma URL do PostgreSQL não substitui essa URL. O aplicativo não permite transporte HTTP nem redirecionamento de credenciais.

## Publicação no Render

O serviço gratuito está publicado em `https://marvel-lobby-api.onrender.com`. Em 05/10/2026, a imagem Docker foi construída no Render, o processo iniciou com a Aiven e `/health` respondeu por HTTPS; `/v1/me` sem sessão respondeu 401. O guia está em [docs/RENDER_SETUP.md](../docs/RENDER_SETUP.md). O serviço usa `rootDir: backend`, Dockerfile `Dockerfile` e contexto `.`; variáveis PG e certificado CA ficam nos segredos da hospedagem, com `PGSSLROOTCERT=/etc/secrets/ca.pem`. O `render.yaml` oferece uma alternativa de serviço Node. A hospedagem encerra HTTPS e encaminha ao processo na porta configurada.

Defina `TRUST_PROXY` apenas com os endereços/CIDRs documentados do proxy real da hospedagem. Sem essa variável, cabeçalhos de IP encaminhado não são confiados. Não use confiança irrestrita: isso permitiria contornar o limite de tentativas. Os limites são em memória por processo; esta versão é adequada para uma instância. Múltiplas instâncias exigirão um limitador compartilhado. Reserve memória para o scrypt (até duas operações de aproximadamente 128 MiB cada), além do Node.

Para ativar a identidade online, acrescente ao `local.properties` do Android (já configurado neste ambiente):

```properties
LOBBY_API_BASE_URL=https://marvel-lobby-api.onrender.com
```

Use a origem sem caminho, consulta ou credenciais. Recompile o app. Com a propriedade vazia/ausente, o acesso local atual permanece disponível; botões de conexão online não aparecem. Nenhuma credencial PG é incluída no APK.

## Contrato da primeira fase

Todas as respostas são JSON; falhas usam `{ "error": { "code": "...", "message": "..." } }`. Requisições autenticadas exigem `Authorization: Bearer <accessToken>`. Os IDs são UUIDs emitidos pelo servidor, independentes de alterações de e-mail/username.

| Método e caminho | Comportamento |
|---|---|
| `POST /v1/auth/register` | `name`, `username`, `email`, `password`, bio/avatar opcionais; cria conta e sessão |
| `POST /v1/auth/login` | `email`, `password`; autentica e cria sessão |
| `POST /v1/auth/refresh` | `refreshToken`; renova e invalida a sessão anterior |
| `POST /v1/auth/logout` | Revoga a família da sessão atual |
| `GET /v1/me` | Perfil privado da conta autenticada, incluindo e-mail |
| `PATCH /v1/me` | Atualiza `name`, `username`, `bio`, `avatarId` |
| `PATCH /v1/me/credentials` | Exige `currentPassword`, recebe `email`, `newPassword` opcional e `profile` opcional; altera tudo na mesma transação, revoga sessões antigas e devolve uma sessão nova |
| `GET /v1/users/:uuid` | Perfil público: `id`, `name`, `username`, `bio`, `avatarId`, `joinedAt`; nunca e-mail/senha/tokens |

Cadastro/login/refresh/alteração de credenciais devolvem `user`, `accessToken`, `refreshToken`, `accessExpiresAt` e `refreshExpiresAt`. Access token dura 15 minutos; refresh token tem prazo total de 30 dias. Tokens aleatórios são guardados como hash SHA-256 no servidor. Renovação usa família com rotação e revoga descendentes ao detectar reutilização. Alterar e-mail/senha exige a senha atual e encerra sessões anteriores em todos os aparelhos.

Senhas usam scrypt com salt aleatório, N=131072, r=8, p=1. Username é normalizado sem `@`, minúsculo, globalmente único, 3–24 letras ASCII/números/sublinhados. Bio permite até 280 caracteres Unicode. Avatares aceitam apenas os IDs do catálogo aprovado do app, ou null. Login inválido não revela se o e-mail existe. Não há recuperação de senha por e-mail/verificação de e-mail nesta fase; essa infraestrutura precisa ser definida antes de uma distribuição pública ampla.

## Android e conta antiga

Os tokens são cifrados com AES-GCM e chave Android Keystore. Room guarda só o perfil e a associação entre conta local e UUID remoto; DataStore guarda a seleção da sessão. Um perfil já salvo continua disponível offline. Operações online exigem rede e uma sessão válida.

Perfil → Conectar conta online pede a senha da conta local e permite criar uma identidade ou entrar em uma conta online existente. A associação é explícita e transacional: mescla favoritos/histórico, preserva conversas **de IA** e bloqueia associar a mesma conta local a outro UUID. Esses dados continuam apenas no aparelho. Trocar e-mail remoto preserva o UUID e sua biblioteca. Conversas de IA não são mensagens privadas entre usuários.

## Verificação

```powershell
npm test
```

Os testes exercitam a API completa com PostgreSQL temporário em PGlite e scrypt real. Não acessam nem criam usuários na Aiven. Cobrem isolamento, duplicidade, validação, perfil público, edição atômica de credenciais/perfil, rotação/reutilização/revogação, expiração, conta desativada e limite de tentativas. A conexão Aiven foi conferida separadamente com consultas somente de leitura.


## Comunidade e mensagens diretas

Instale `database/004_community.sql` como administrador no banco `marvel_mobile`; o usuário confirmou a execução em 05/10/2026. A migração é transacional e pode ser repetida. Não reutiliza nem apaga as tabelas de conversa de IA. Sem a versão 3, os endpoints sociais retornam `503 COMMUNITY_NOT_READY`; a identidade continua disponível.

Todos os endpoints abaixo exigem Bearer token. Perfis incluem apenas nome, @username, bio, avatar aprovado e data de entrada. A busca não consulta e-mails. Seguir é unilateral, idempotente e não exige aprovação; seguir a própria conta é proibido.

| Método e caminho | Comportamento |
|---|---|
| `GET /v1/community/people` | Busca `query` por nome/@username; cursor `after`, `limit` de até 30 |
| `GET /v1/community/users/:id` | Perfil, seguidores/seguindo, relação com quem consulta |
| `GET /v1/community/users/:id/followers` | Seguidores paginados, busca e cursor |
| `GET /v1/community/users/:id/following` | Pessoas seguidas, busca e cursor |
| `POST /v1/community/users/:id/follow` | Segue a pessoa |
| `DELETE /v1/community/users/:id/follow` | Deixa de seguir |
| `POST /v1/community/conversations` | `{userId}`; abre ou recupera a conversa única do par |
| `GET /v1/community/conversations` | Caixa de entrada, `offset`, até 30 itens, última mensagem e não lidas |
| `GET /v1/community/conversations/:id/messages` | Até 40 mensagens, `before` ou `after` (IDs como strings); ordenadas cronologicamente, com leitura do destinatário |
| `POST /v1/community/conversations/:id/messages` | `{text,clientId}`; UUID de envio impede duplicar tentativas; texto de 1–2000 caracteres Unicode |
| `POST /v1/community/conversations/:id/read` | `{lastId}` como string; leitura só avança e deve apontar para mensagem da conversa |
| `WSS /v1/community/live` | Bearer no cabeçalho; sinais `ready`, `community`, `messages`, `read` |

RLS limita conversa, texto e leitura aos dois participantes. A API valida a sessão dentro da transação e verifica o destinatário ativo. O envio usa um bloqueio transacional por conversa antes de alocar IDs, evitando lacunas na busca incremental causadas por commits fora de ordem. Reenvio com a mesma identidade e texto devolve a mensagem salva; reutilização para outro texto retorna 409. Limite de 40 envios/minuto por IP, além do limite global.

WebSocket envia apenas sinais, sem corpo das mensagens. Depois do sinal, o Android consulta HTTPS autenticado; preserva rascunho/teclado e mescla respostas repetidas. Ao reconectar, recupera mensagens novas. Leitura é enviada quando o usuário está vendo o fim da conversa. O histórico de mensagens diretas fica no PostgreSQL e é recuperável em outro dispositivo com a mesma conta; os chats de IA continuam locais. Rascunhos e cache social são temporários, não há envio offline em segundo plano.

O realtime e o limitador usam memória de uma instância. Escalar requer distribuição dos eventos/limites. O plano gratuito pode suspender o servidor, causando demora na reconexão. Transporte usa TLS; não há criptografia de ponta a ponta, anexos, moderação, bloqueio de usuários, push ou feed neste pedido.
