# API do Marvel Lobby — identidade online

Este serviço é a ponte HTTPS entre o Android e o PostgreSQL da Aiven. O celular nunca recebe usuário/senha do PostgreSQL. A primeira fase cobre cadastro, login, sessões e perfil. Amigos, solicitações, mensagens privadas, notificações e sincronização da biblioteca ainda não fazem parte desta versão. O catálogo Comic Vine, o chat Gemini e a tradução Groq continuam separados.

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

## Publicação pendente

Foi escolhido começar pelo plano gratuito do Render; ainda não existe uma API pública. O guia está em [docs/RENDER_SETUP.md](../docs/RENDER_SETUP.md), e `render.yaml` prepara um serviço Node com `rootDir: backend`. O `Dockerfile` oferece uma alternativa de contêiner, mas a imagem não foi construída neste ambiente. Construa com o contexto `backend`; configure as variáveis PG por segredos da hospedagem e monte o CA fora da imagem, por exemplo com `PGSSLROOTCERT=/run/secrets/ca.pem`. O serviço deve encerrar HTTPS no proxy da plataforma e encaminhar ao processo na porta configurada.

Defina `TRUST_PROXY` apenas com os endereços/CIDRs documentados do proxy real da hospedagem. Sem essa variável, cabeçalhos de IP encaminhado não são confiados. Não use confiança irrestrita: isso permitiria contornar o limite de tentativas. Os limites são em memória por processo; esta versão é adequada para uma instância. Múltiplas instâncias exigirão um limitador compartilhado. Reserve memória para o scrypt (até duas operações de aproximadamente 128 MiB cada), além do Node.

Depois da publicação, acrescente ao `local.properties` do Android:

```properties
LOBBY_API_BASE_URL=https://seu-endereco-da-api
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
