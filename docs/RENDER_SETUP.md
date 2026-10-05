# Hospedar gratuitamente a API

Você já tem o banco **marvel_mobile** na Aiven. O Render vai executar o programa que conecta o celular a esse banco. Não crie outro PostgreSQL no Render.

## 1. Código no GitHub

O Render precisa receber o código de um repositório. Use um repositório privado. Antes de publicar, mantenha fora dele `local.properties`, qualquer `.env`, `secrets/`, arquivos de certificado, `node_modules/` e pastas de compilação. O projeto tem regras de exclusão, e `backend/.gitignore` também protege a pasta caso ela seja publicada separadamente.

Repositório do projeto: https://github.com/BrenoLeal4sl-dev/Marvel-Lobby. API publicada em https://marvel-lobby-api.onrender.com; verificação HTTPS concluída em 05/10/2026.

## 2. Criar o serviço

1. Entre em https://dashboard.render.com e escolha **New → Web Service**.
2. Conecte o repositório **BrenoLeal4sl-dev/Marvel-Lobby**.
3. Nome: `marvel-lobby-api`; linguagem: **Docker**; plano: **Free**.
4. **Root Directory**: `backend`, pois o repositório contém o projeto Android e a API.
5. **Dockerfile Path**: `Dockerfile`; **Docker Build Context Directory**: `.`. Os caminhos são relativos a `backend`, definido acima.
6. Deixe **Docker Command** e **Pre-Deploy Command** vazios: a imagem já define a inicialização.
7. **Health Check Path**: `/health`.

O serviço atual foi criado manualmente com Docker. O `render.yaml` na raiz oferece uma alternativa Node via Blueprint, com build `npm ci --include=dev && npm run build` e start `node dist/src/server.js`. Ele não cria banco nem inclui senhas. Configure o certificado antes de iniciar o serviço.

## 3. Configuração privada do banco

Em **Environment → Environment Variables**, configure:

| Nome | Valor |
|---|---|
| `NODE_ENV` | `production` |
| `HOST` | `0.0.0.0` |
| `PGHOST` | Host da Aiven, o mesmo do arquivo local `database/.env` |
| `PGPORT` | `11716` |
| `PGDATABASE` | `marvel_mobile` |
| `PGUSER` | `marvel_lobby_api` |
| `PGPASSWORD` | Senha desse usuário de serviço, do arquivo local `database/.env` |
| `PGSSLMODE` | `verify-full` |
| `PGSSLROOTCERT` | `/etc/secrets/ca.pem` |

O Render define `PORT` automaticamente. Não use a senha do `avnadmin`. Não precisa cadastrar as chaves Comic Vine/Gemini/Groq para essa primeira fase: este servidor cuida apenas das contas do Marvel Lobby.

Em **Environment → Secret Files → Add Secret File**, nomeie o arquivo **ca.pem** e cole o conteúdo do `database/secrets/ca.pem` do projeto. Use esse arquivo já validado, preservando as linhas BEGIN/END e as quebras de linha. Não publique seu conteúdo no repositório.

Deixe `TRUST_PROXY` ausente até identificar os endereços confiáveis do proxy da hospedagem. Nessa configuração conservadora, acessos compartilhados pelo proxy podem atingir o limite de tentativas em conjunto; para poucos usuários de teste, aguarde um minuto ao receber limite. Uma distribuição maior exige ajustar isso com a configuração real do provedor, sem confiar irrestritamente em cabeçalhos enviados pelo cliente.

## 4. Ativar no app

O serviço está **Live**. `https://marvel-lobby-api.onrender.com/health` retornou `{"status":"ok","service":"Marvel Lobby"}`. Essa origem está configurada no `local.properties` deste ambiente. Para outro checkout, acrescente `LOBBY_API_BASE_URL=https://marvel-lobby-api.onrender.com` e recompile. A URL não contém credenciais; nenhuma senha do banco entra no aplicativo.

Com a API ativa: a tela de acesso oferecerá conta online/local; no perfil de uma conta local aparecerá **Conectar conta online**. Confirme a senha local, crie uma identidade online ou conecte uma conta já existente. O app preserva favoritos, histórico e chats de IA neste aparelho. Amigos/mensagens privadas serão implementados nas fases seguintes.

## Limite do plano gratuito

O serviço gratuito pausa depois de 15 minutos sem tráfego e pode levar cerca de um minuto para reiniciar. A primeira tentativa de login pode atingir o timeout do app; aguarde e tente novamente. O banco continua na Aiven. O Render recomenda plano pago para produção; nesta etapa, o Free serve para experimentar.

Referências oficiais: [Web Services](https://render.com/docs/web-services), [ambiente e arquivos privados](https://render.com/docs/configure-environment-variables), [plano gratuito](https://render.com/docs/free), [Blueprint](https://render.com/docs/blueprint-spec).
