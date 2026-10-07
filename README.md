# Marvel Lobby

Aplicativo Android nativo em Kotlin, com interface baseada no [Figma do projeto](https://www.figma.com/design/fIxAXlsMLwvvgaFZBp6Rqb/Projeto---Marvel-Lobby), fonte Plus Jakarta Sans e catálogo real da Comic Vine. Android 13 (API 33) ou superior.

## Executar

Abra o projeto no Android Studio e execute o módulo `app`. O arquivo local `local.properties`, ignorado pelo controle de versão, deve conter `sdk.dir`, `MARVEL_API_KEY` (chave **Comic Vine**), `GEMINI_API_KEY` (Marvel AI) e `GROQ_API_KEY` (traduções). Opcionalmente, `GEMINI_MODEL` altera o modelo de chat padrão `gemini-2.5-flash`, e `GROQ_TRANSLATION_MODEL` altera o modelo de tradução padrão `openai/gpt-oss-20b`. Após mudar as chaves, recompile. Nenhum valor de chave deve ser versionado.

As propriedades são injetadas em BuildConfig durante a compilação. Elas não estão escritas nos arquivos Kotlin; um APK cliente, porém, não é um cofre de credenciais. Uma distribuição pública com chaves compartilhadas requer um backend intermediário.

```powershell
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew.bat :app:connectedDebugAndroidTest
```

Neste Windows, o JBR apresentou falha de socket local ao iniciar o Gradle. A execução foi possível com um diretório curto para os sockets:

```powershell
$env:JAVA_HOME='C:/Program Files/Android/Android Studio/jbr'
$env:JAVA_TOOL_OPTIONS="-Djdk.net.unixdomain.tmpdir=$((Join-Path $PWD 'build') -replace '\\','/')"
./gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Fluxos

- Escolha de idioma antes do primeiro acesso, splash automática, Welcome, cadastro/login online e sessão persistente.
- Home, Explore, busca global por tipo, personagens, equipes, poderes e arcos, com detalhes e relacionamentos navegáveis.
- Paginação `limit`/`offset`, ordenação de personagens, filtro Marvel e filtro por poder, estados de carregamento, vazio, erro e cache offline.
- Favoritos de quatro tipos, histórico recente, perfil com avatares Marvel aprovados, edição de nome/e-mail/senha, logout, aparência e idioma.
- Comunidade pela Home ou Perfil: busca por nome/@usuário, bio pública, seguidores/seguindo, caixa de entrada, chat direto, mensagens não lidas e confirmações de leitura. Exige conta online.
- Marvel AI com mensagens, espera, erro e tentativa novamente. Aberto de um registro, envia seus dados Comic Vine como contexto para o Gemini e mostra as fontes consultadas.

Configure `LOBBY_API_BASE_URL` no `local.properties` para habilitar cadastro/login online e os serviços sociais. A API está publicada em `https://marvel-lobby-api.onrender.com`, conectada ao banco Aiven; essa origem já está configurada localmente neste ambiente. Outros checkouts devem configurar a mesma propriedade e recompilar. Contratos e execução estão em [backend/README.md](backend/README.md). Não há fallback para criação de conta local.

Login e cadastro oferecem acesso online. Contas locais antigas continuam compatíveis e podem ser associadas a uma conta online. Tokens são cifrados no Android Keystore. Room v6 mantém biblioteca, conversas e alterações pendentes neste aparelho. A sincronização privada de histórico e conversas de IA é opcional, ativada em Configurações; sair não apaga os registros locais.

### Recursos sociais e sincronização

- Nos detalhes de personagens, equipes, poderes e arcos, **Compartilhar um registro** abre a seleção de destinatário. O chat recebe um card clicável com a identidade Comic Vine, sem reenviar a descrição inteira.
- **Comunidade → Atividades** mostra novos favoritos das pessoas seguidas que optaram por compartilhar. **Configurações → Privacidade das atividades** controla a participação; desativar remove as atividades anteriores.
- **Comunidade → Notificações** reúne seguidores e mensagens, com não lidas, paginação e marcação de leitura. São notificações internas, não push do Android.
- **Configurações → Sincronização na nuvem** permite sincronizar histórico e conversas de IA entre aparelhos da mesma conta. O envio fica desativado por padrão. Usa snapshots compactos do catálogo e texto integral das mensagens dentro dos limites documentados da API; registros grandes demais permanecem locais com erro de sincronização.
- Alterações pendentes e exclusões sobrevivem a reinícios. Versões impedem sobrescrever alterações concorrentes; respostas divergentes em uma conversa de IA são preservadas em conversas separadas. A sincronização ocorre ao abrir o app, periodicamente enquanto ele está em primeiro plano ou pelo botão **Sincronizar agora**. Pausar vale para a conta e mantém as cópias já enviadas.
- Recuperação de senha/e-mail não faz parte deste escopo, conforme solicitado.

Instalação adicional: execute `database/006_social_extensions.sql` e depois `database/007_private_sync.sql` como administrador no banco `marvel_mobile`, antes de publicar esta revisão. Ambos foram confirmados executados pelo usuário em 06/10/2026.

## Organização

Os scripts PostgreSQL estão em [database/README.md](database/README.md), com instruções para Aiven e pgAdmin. A API e suas instruções de execução estão em [backend/README.md](backend/README.md); planejamento social em [docs/SOCIAL_PLAN.md](docs/SOCIAL_PLAN.md).

- `presentation/`: estado, ViewModel, renderização das telas e componentes nativos reutilizáveis.
- `data/repository/`: catálogo/cache, contas, biblioteca local e integração de IA.
- `data/api/`: transporte e mapeamento Comic Vine.
- `data/local/`: Room, DataStore e hash de senha.
- `data/remote/`: transporte HTTPS e contrato da identidade online; as credenciais do banco ficam somente no servidor.
- `data/model/`: modelos tipados e relacionamentos.

A UI observa StateFlow; chamadas de rede e banco executam fora da thread principal. Pesquisas têm debounce e proteção contra respostas antigas. O cache de catálogo em disco tem validade de dez minutos e limite de 200 entradas; dados locais de favoritos/histórico não dependem desse cache.

## Particularidades dos dados

Comic Vine não oferece filtro confiável de publisher em todos os recursos utilizados. O aplicativo confere o publisher retornado (Marvel, id 31) após a paginação, com varredura limitada por ação. Uma página pode não conter resultados Marvel e ainda permitir carregar mais. Poderes são uma taxonomia compartilhada; seus personagens relacionados podem incluir outras editoras.

O filtro por poder verifica detalhes dos personagens da página atual e pode levar mais tempo. `Regeneration` encontra `Healing`, e `Teleportation` encontra `Teleport`, preservando os nomes oficiais exibidos pela API. Informações/relacionamentos ausentes não são inventados. Descrições HTML são convertidas em texto.

Personagens começam pelos registros atualizados recentemente (`date_last_updated:desc`), com A–Z e Z–A disponíveis. A API ignorou a tentativa anterior de ordenar por quantidade de aparições; essa opção foi removida. Pesquisas de personagens e equipes usam `/search/`, `resources` e `query`, preservando a relevância do serviço. Poderes e arcos usam o filtro de nome de suas coleções; o índice genérico de arcos retornou vazio na consulta real de Civil War. As buscas por categoria são sequenciais e param diante de bloqueio de acesso ou limite de consultas. O transporte serializa requisições e aplica uma pausa de pelo menos 30 segundos após essas respostas, respeitando `Retry-After` numérico quando informado.

O caminho de navegação é salvo no SavedStateHandle para voltar pelas telas após recriação do processo pelo Android. Somente rotas são restauradas por esse mecanismo; senhas, formulários e conversas não são persistidos nele. Salvar o perfil retorna à tela de origem, e voltar sem aplicar alterações descarta os rascunhos de perfil/filtros. As relações de equipes podem incluir registros históricos; não são apresentadas como uma lista garantida de membros atuais. Edições mostram série e número quando fornecidos, e favoritos antigos de edições continuam removíveis por uma aba adicional.

Buscas canceladas são removidas da espera antes de abrir uma conexão; uma chamada que já começou continua limitada pelos timeouts do transporte. Durante o bloqueio, a busca global ainda aproveita páginas das outras categorias já salvas para o mesmo termo, indicando resultado parcial. Detalhes salvos continuam legíveis após vencer o cache quando a rede falha. No Marvel AI aberto por um detalhe, o contexto já carregado é reutilizado sem uma nova consulta obrigatória à Comic Vine.

Um HTTP 403 com HTML do Cloudflare é tratado como bloqueio da conexão, não como prova de chave inválida. Em 01/10/2026 ocorreram bloqueios intermitentes. Na revisão seguinte, listagem recente, busca de Spider-Man e detalhe de Flight responderam HTTP 200/status 1. Trocar a mensagem ou a construção da busca não remove um bloqueio externo quando ele ocorre.

Notificações não foram incluídas porque não existe um comportamento de notificações definido no escopo. As descrições originais vêm da Comic Vine. Em português, trechos são traduzidos pela Groq e mantidos em cache, com opção de ver o original quando a tradução estiver indisponível. Nenhuma tradução recorre ao Gemini. Textos completos são paginados para preservar o conteúdo sem gerar uma única página enorme. Conversas de IA continuam no Gemini e são salvas no Room por conta, com contexto e fontes; podem ser reabertas ou excluídas pelo histórico.

## Design e validação

Os contextos Figma obtidos estão em `docs/figma/`. Cores, dimensões, ícones exportados e fonte foram aplicados aos componentes. A cota do conector Figma Starter interrompeu a inspeção individual de parte dos 57 estados; não há validação visual completa desses estados. Consulte `docs/IMPLEMENTATION_STATUS.md` para as evidências e limites de verificação.

Licença da fonte: `docs/licenses/PlusJakartaSans-OFL.txt`. Conteúdo e imagens pertencem aos respectivos titulares; projeto educacional independente.


## Perfil, idiomas e chat

A Home alinha a logo à esquerda e exibe o avatar atual da conta. O seletor no lápis do perfil oferece oito imagens originais Comic Vine incluídas no app, com carrossel e prévia circular; não abre a galeria pessoal. Origens das imagens em `app/src/main/assets/avatars/catalog.json`. O futuro backend deverá mapear o ID do avatar aprovado; a URI dos assets é local ao Android.

O perfil mostra nome e @username, sem e-mail ou botão de revelação. Contas existentes recebem um username automaticamente a partir do nome, com sufixo aleatório; ele permanece salvo após trocar nome/e-mail. Pode ser editado em Configurações → Editar perfil, usando de 3 a 24 letras, números ou sublinhados. A unicidade é local ao aparelho.

Configurações concentra a edição de conta e mostra Sair em vermelho no final. E-mail e campos de senha ficam mascarados somente na edição da conta. Revelar informações solicita a credencial de bloqueio do Android quando configurada; aparelhos sem bloqueio não recebem esse desafio. A senha já armazenada não pode ser recuperada/exibida: a interface permite digitar a senha atual e definir outra. Trocar e-mail ou senha exige a senha atual da conta, independentemente do bloqueio do aparelho. A troca de e-mail move favoritos, histórico e conversas na mesma transação Room.

O chat tem identidade escura própria, inclusive no modo claro, gradientes, campo fixo acima do teclado e três pontos animados durante a resposta. A estrutura e o campo são preservados durante atualizações de estado para manter a conexão com o teclado. A navbar não aparece dentro da conversa; entrada/saída da IA usam fade e movimento suave, respeitando animações desativadas no Android. A tela anterior deixa de receber toques durante a transição e é removida ao terminar. Atualizações de loading não reiniciam a animação. O cabeçalho oferece histórico e nova conversa por ícones de tamanho/alinhamento consistentes. Conversas já encerradas antes dessa atualização não podem ser recuperadas, pois não eram persistidas.

Room versão 3 usa migrações explícitas 1→2→3, sem apagar contas ou registros anteriores. A migração 2→3 inclui usernames com índice único. Conversas e biblioteca continuam locais e separadas por conta. Traduções enviam apenas trechos do catálogo à Groq, sem e-mail, senha ou mensagens do chat; as respostas de chat do Gemini seguem o idioma selecionado. Nomes oficiais de séries/personagens podem permanecer no idioma de origem.

O cliente de tradução segue a [API oficial da Groq](https://console.groq.com/docs/api-reference), com autorização em cabeçalho HTTPS, timeouts e sem seguir redirecionamentos. O cache anterior continua válido para evitar novas chamadas desnecessárias. Requisições concorrentes para o mesmo trecho reutilizam o resultado salvo. Limite de uso, acesso recusado, resposta vazia ou incompleta mantêm o texto original e a ação de tentar novamente; falhas não são salvas como tradução.
# Rift Arena

A Home e os detalhes do Homem-Aranha oferecem acesso à Rift Arena: sobrevivência 2D com joystick, ataque, dash, especial, experiência, melhorias e chefes. Treino funciona offline; partidas competitivas exigem sessão online e salvam resultados para sincronização. Inclui rankings, desafios assíncronos entre pessoas seguidas, estatísticas de perfil e cartões no chat. A migração do servidor está em `database/008_rift_arena.sql`; detalhes e limitações em `docs/RIFT_ARENA.md`. Os personagens da arena usam visuais provisórios desenhados em Canvas nesta primeira versão.
