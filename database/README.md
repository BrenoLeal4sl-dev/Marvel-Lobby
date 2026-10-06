# Banco do Marvel Lobby — Aiven + pgAdmin

## Favoritos públicos — atualização atual

Com a comunidade instalada (versão 3), execute inteiro `005_public_favorites.sql` no Query Tool do banco **marvel_mobile**, como administrador. A migração instala a versão **4**, preserva contas e mensagens e concede as permissões ao usuário `marvel_lobby_api`.

Ela cria uma projeção mínima dos favoritos compartilhados (`public_favorites`) e a view de visibilidade (`favorite_sharing_profiles`). A preferência existente `show_favorites` permanece desativada por padrão. Cada pessoa pode ativar ou ocultar no Profile. O Android publica apenas nome, imagem, tipo e ID do catálogo, sem histórico, e-mail, descrições completas ou chats. RLS limita a escrita ao dono e a leitura de outras contas aos perfis que optaram por compartilhar. Não é sincronização completa da biblioteca entre aparelhos.

O app usa Room versão 5 para guardar a bio local e a fila de alterações de favoritos, preservando os dados da versão anterior.

Esta pasta prepara o PostgreSQL do backend. Não conecta o Android ao banco e não migra automaticamente as contas locais existentes. O app continua funcionando com Room até a integração da Fase 1. As tabelas `conversations` e `messages` guardam Marvel AI; mensagens entre amigos terão tabelas próprias em uma fase seguinte.

## Banco novo: arquivo único para copiar no pgAdmin

Abra **000_setup_phase1.sql** no Query Tool da conexão administrativa da Aiven e execute o arquivo inteiro uma única vez. Ele contém a estrutura inicial e a evolução de identidade da Fase 1, sem senhas ou criação de usuários PostgreSQL. As onze relações criadas são dez tabelas e a view somente leitura `public_profiles`.

Se `marvel_lobby` já existe porque você executou `001_initial.sql`, execute somente **003_social_identity.sql**, usando o mesmo administrador/migrador. Não execute o instalador completo novamente e não apague o schema.

Depois, crie na Aiven um service user **marvel_lobby_api**. Ainda conectado como administrador no pgAdmin, execute **002_runtime_permissions.sql**. O script de permissões pode ser executado novamente; ele não altera senhas e impede usar um administrador como usuário da API.

Confirme a instalação:

```sql
SELECT version, description, applied_at
FROM marvel_lobby.schema_migrations ORDER BY version;
```

O resultado deve mostrar versões **1 e 2**. A senha e o certificado ficam locais: copie `.env.example` para `database/.env`, preencha os campos PG e salve o certificado CA da Aiven em `database/secrets/ca.pem`. `PGUSER` é `marvel_lobby_api`, e não `avnadmin`. Não coloque esses dados no `local.properties` do Android. Informe apenas que a instalação terminou e que o arquivo foi preenchido.

`000_setup_phase1.sql` é gerado a partir de `001_initial.sql` + `003_social_identity.sql` por `build_setup.ps1`. Não é reset nem script de sincronização. Nenhum desses scripts publica um backend, cria a camada de amigos/chat ou envia os dados do celular.

## Identidade online — Fase 1

- UUID interno permanente e username único globalmente, em minúsculas, sem `@`, com 3–24 letras ASCII/números/sublinhados. A aplicação normaliza a entrada antes de gravar.
- Nome, bio de até 280 caracteres, avatar identificado pela seleção aprovada do Android e data de entrada proveniente de `users.created_at`.
- View `public_profiles` expõe somente UUID, nome, username, bio, avatar e data de entrada. O usuário da API só tem SELECT nessa view; tabelas privadas continuam protegidas por RLS. Acesso HTTP exige autenticação no futuro backend.
- Atividade e favoritos começam privados. A exposição dos interesses e atividades será implementada na Fase 4, respeitando essas preferências.
- Access e refresh tokens opacos: banco recebe somente hashes SHA-256. Expiração, rotação, revogação, validação de senha e rate limit serão regras do backend, não funcionalidades simuladas no SQL.
- A migração preserva dados existentes e revoga sessões antigas sem access token. Ela não importa contas Room nem hashes PBKDF2 do Android.

## Executar pelo pgAdmin

1. Crie o serviço PostgreSQL na Aiven e aguarde ficar pronto. Os scripts usam recursos nativos de PostgreSQL 14 ou superior, sem extensões ou privilégios de superusuário.
2. Registre a conexão no pgAdmin com host, porta, banco e usuário fornecidos pela Aiven. Pode usar o banco `defaultdb`; todas as tabelas ficam no schema próprio `marvel_lobby`. Não precisa criar tabelas manualmente.
3. Configure SSL conforme os dados da Aiven. Para validar também o servidor, use `verify-full` com o certificado CA do serviço. O host deve ser o nome fornecido pela Aiven. Não desative SSL para resolver erros de conexão.
4. Conectado como `avnadmin`/administrador, abra o **Query Tool** do banco escolhido, carregue `000_setup_phase1.sql` e execute o arquivo inteiro. Ele aplica as duas migrações, sem apagar tabelas existentes.
5. Atualize a árvore de schemas do pgAdmin. Devem aparecer dez tabelas e a view `public_profiles` em `marvel_lobby`.
6. Na Aiven, crie um **service user** chamado `marvel_lobby_api`, sem privilégios de administrador e sem associação ao usuário dono do schema. Guarde a senha localmente.
7. Ainda na conexão administrativa do pgAdmin, execute `002_runtime_permissions.sql`. Esse arquivo concede as permissões para o futuro backend e rejeita um usuário que possa ignorar o isolamento por linha.

O primeiro arquivo é uma migração inicial, não um reset: uma segunda execução falha porque o schema já existe. Em caso de erro, execute `ROLLBACK;` antes de tentar novamente. Não apague o schema para atualizar o sistema; alterações posteriores terão novas migrações.

Consulta de conferência:

```sql
SELECT version, description, applied_at FROM marvel_lobby.schema_migrations;
SELECT table_name FROM information_schema.tables
WHERE table_schema = 'marvel_lobby' ORDER BY table_name;
```

## O que foi criado

| Tabela | Finalidade |
|---|---|
| `users` | Identidade, e-mail único sem distinção de maiúsculas, hash de senha |
| `profiles` | Nome, username global, bio e avatar aprovado; URL legada preservada |
| `preferences` | Tema, idioma e privacidade de atividades/favoritos |
| `sessions` | Hashes de access/refresh tokens, expiração, família de rotação e revogação |
| `catalog_cache` | Cache temporário de registros Comic Vine |
| `favorites` | Quatro tipos de favoritos, com snapshot independente do cache |
| `view_history` | Conteúdos abertos, última visita e contador |
| `conversations` | Conversas do Marvel AI e contexto opcional |
| `messages` | Mensagens e fontes da conversa, com dono consistente |
| `schema_migrations` | Versão da estrutura |

A view `public_profiles` complementa essas tabelas, expondo apenas os campos públicos do perfil.

Há três funções: contexto do usuário autenticado, validação da identidade dos snapshots e atualização de data/versão. Triggers mantêm `updated_at`, preservam `created_at` e incrementam `version`. Não há chamadas à Comic Vine/Gemini dentro do PostgreSQL.

Snapshots usam o formato do modelo do app: `id`, `type` e `name` são obrigatórios e devem corresponder ao registro salvo. A coluna `resource_type` usa `character`, `team`, `power`, `story_arc`, etc.; o `type` no JSON aceita a capitalização atual do Kotlin. Não gravar chaves de API, senhas ou tokens nesses JSONs.

## Contrato obrigatório do backend

- Somente o backend utiliza o usuário `marvel_lobby_api`. O Android acessa endpoints HTTPS e nunca recebe credenciais PostgreSQL. `avnadmin` é reservado para administração/migrações.
- `users` e `sessions` são acessíveis ao backend confiável para login/validação de sessão; não expor consultas arbitrárias nem suas linhas em endpoints públicos. Verificação de senha, rotação de tokens, rate limiting e autorização dos endpoints ainda serão implementados.
- Usar hash de senha codificado por biblioteca adequada (por exemplo Argon2id), nunca senha em texto. A constraint de tamanho do banco não substitui a verificação do algoritmo. Refresh tokens devem ser aleatórios; guardar apenas seu hash SHA-256 de 32 bytes.
- Em cada operação privada, abrir uma transação e definir o UUID obtido da autenticação usando `SELECT set_config('app.user_id', $1, true)`, com parâmetro. Executar as consultas na **mesma conexão/transação** e finalizar com commit/rollback. O terceiro argumento `true` impede que o contexto vaze para outra requisição no pool.
- As políticas RLS protegem perfis, preferências, favoritos, histórico, conversas e mensagens. Sem contexto, o usuário do backend não enxerga essas linhas. O administrador/dono do schema pode ignorar RLS; não usar essa conexão na API.
- No cadastro, gerar o UUID no servidor, definir o contexto e inserir usuário, perfil e preferências na mesma transação. Não confiar no `user_id` enviado pelo celular.
- Favoritos, histórico e conversas têm `deleted_at` para propagar exclusões. Consultas normais filtram `deleted_at IS NULL`; sincronização também precisa receber exclusões. `version` permite controle otimista (`WHERE version = $versaoEsperada`). Datas/versões por linha não constituem, sozinhas, um cursor global sem perdas em transações concorrentes: a estratégia de sincronização será implementada no backend.
- Inserir mensagens apenas após verificar que a conversa pertence ao usuário e está ativa. Serializar envios concorrentes por conversa e atualizar a conversa ao acrescentar mensagens. O UUID da mensagem pode servir como chave de idempotência em reenvios.
- O cache pode ser removido por expiração sem apagar favoritos/histórico. Agendar a limpeza de sessões vencidas e definir retenção/remoção física de registros excluídos na implementação do backend; o script não cria jobs externos.
- Avatares pessoais não são enviados: o backend aceita apenas `avatar_id` da seleção aprovada. O Android mapeia esse identificador para o asset empacotado; não é necessário upload nesta fase. Contas locais e visitantes precisam de um fluxo explícito de migração/vinculação; não copiar hashes PBKDF2. A senha fornecida pelo usuário será verificada localmente e, com sua confirmação, usada na autenticação/cadastro HTTPS com hash próprio no servidor.

## Credenciais

`database/.env.example` é apenas um modelo. O backend em `backend/` já lê os valores do arquivo local `database/.env`. `.env` e pastas `secrets/` estão ignorados pelo Git. Não coloque credenciais do PostgreSQL no Android. Para informar a configuração, basta indicar host, porta, nome do banco, versão e que os scripts executaram; a senha fica no arquivo local ou nos segredos da hospedagem.

## Testes locais sem Aiven

```powershell
npm.cmd install --prefix build/database-check --no-audit --no-fund --ignore-scripts @electric-sql/pglite@0.5.8
node database/tests/schema.test.cjs
node database/tests/social-identity.test.cjs
```

Os testes criam um PostgreSQL em memória com PGlite, aplicam a migração como usuário sem superprivilégios e verificam RLS, vínculos, duplicatas, versões e cascatas. Não usam contas nem dados reais, e não testam conectividade/SSL/permissões específicas da sua instância Aiven.

Validação em 02/10/2026: 17 verificações aprovadas em PostgreSQL 18.3 (PGlite 0.5.8). A compatibilidade com versões anteriores não foi executada nesta etapa.

Validação em 05/10/2026: os 17 testes existentes e 13 verificações novas da Fase 1 passaram, totalizando 30. Instalador completo, migração com dados anteriores, unicidade/formato de usernames, avatares, bios, view pública, RLS, privacidade e tokens foram verificados. Nenhum script foi executado na Aiven; conectividade, certificado e permissões específicas do serviço ainda precisam ser conferidos após a instalação pelo usuário.

Referências: [pgAdmin na Aiven](https://aiven.io/docs/products/postgresql/howto/connect-pgadmin), [service users](https://aiven.io/docs/products/postgresql/howto/manage-service-users), [privilégios administrativos](https://aiven.io/docs/products/postgresql/concepts/dba-tasks-pg), [RLS PostgreSQL](https://www.postgresql.org/docs/current/ddl-rowsecurity.html).

Atualização da conexão — 05/10/2026: o usuário instalou o esquema no banco **marvel_mobile** e concedeu permissões ao service user **marvel_lobby_api**. A conexão real foi verificada com TLS/hostname e CA válidos, função sem superprivilégios/bypass/posse do schema, versão do esquema e leitura da view pública. O backend iniciou com essa configuração e respondeu ao health check local. Nenhuma conta de teste foi criada na Aiven. A publicação HTTPS no Render está preparada em [docs/RENDER_SETUP.md](../docs/RENDER_SETUP.md), ainda pendente.


## Comunidade — seguir e chat

Após a identidade online e as permissões, execute inteiro `004_community.sql` no Query Tool do banco **marvel_mobile**, conectado como administrador. O usuário confirmou a execução em 05/10/2026. Ele acrescenta a versão 3 com `follows`, `direct_conversations`, `direct_messages` e `direct_reads`, índices, grants restritos e políticas RLS. Preserva contas, catálogo, biblioteca e tabelas de IA. Reexecutar o arquivo não apaga nem recria dados existentes.

O par de participantes é único, seguir não exige aceitação, mensagem usa identidade de envio para idempotência, leitura só avança. API deve verificar uma sessão válida e definir `app.user_id` no mesmo contexto transacional das consultas. Não conceda privilégios administrativos ao usuário da API. Mensagens diretas permanecem no servidor; tabelas antigas de IA não são reutilizadas para elas.
