# Marvel Lobby — implementação e validação

Atualizado em 01/10/2026.

## Revisão de informações e navegação — etapa mais recente

- Extraído AppNavigator, com testes para voltar entre personagem/poder/personagem, favoritos/chat/fontes, edição de perfil, onboarding, troca de autenticação, logout e restauração serializada do caminho de volta. MainViewModel persiste rotas e pilha em SavedStateHandle; não persiste senhas nem chat nesse mecanismo.
- Salvar perfil retorna à origem sem apagar a pilha. Onboarding concluído não reaparece ao voltar. Trocar login/cadastro substitui a tela atual sem criar ciclos. Voltar restaura o carregamento do destino quando necessário. Rascunhos de perfil/filtros são descartados ao voltar sem salvar/aplicar.
- Voltar pelo cabeçalho e pelo sistema compartilham o tratamento de teclado. Restauração de scroll passou a verificar se o callback ainda pertence à tela visível; posição também é guardada na destruição da Activity.
- Operações de autenticação e edição de perfil passaram a participar do cancelamento de jobs no logout; cancelamento não é convertido em erro de formulário.
- Consulta real: Wolverine 1440, nome James Howlett, publisher 31; primeira aparição referência 14667, número 180. Consulta da edição retornou volume The Incredible Hulk, id 2406. Série/número agora são exibidos quando fornecidos. Relacionamentos inválidos ou duplicados são filtrados.
- X-Men 3173 retornou personagens associados. Rótulos agora distinguem personagens de arcos/edições, personagens com um poder e membros/personagens relacionados de equipes; a IA também é orientada a não presumir elenco atual a partir de associações históricas.
- Edições não recebem novos favoritos invisíveis nas quatro categorias suportadas. Favoritos antigos de outros tipos permanecem acessíveis/removíveis por abas adicionais.
- Poderes usam seu ID real tanto na lista quanto nos relacionamentos. Não se informa ausência de descrição se o texto completo estiver disponível.
- Corrigida busca de Story Arcs: `/search/?resources=story_arc&query=Civil War` respondeu vazio; `/story_arcs/?filter=name:Civil War` retornou quatro arcos relacionados, incluindo `"Avengers" Civil War` (40615). Não foi encontrado um título exato `Civil War` nessa resposta; os títulos exibidos continuam sendo os retornados pela API, sem inventar ou renomear o evento principal.
- Cache de páginas v4 e detalhes v2 evita reaproveitar resultados incompatíveis com o mapeamento revisado, preservando Room/favoritos/histórico.

## Correção do Characters vazio — verificação mais recente

Esta etapa substitui as conclusões anteriores sobre indisponibilidade contínua e ordenação por aparições. Após a captura enviada pelo usuário, a API respondeu novamente HTTP 200/status 1.

- Reproduzido: `count_of_issue_appearances:desc` é ignorado no endpoint characters; a resposta começa por IDs antigos, como Lightning Lad 1253 e Dream Girl 1254, ambos DC. O filtro Marvel então descarta registros e a varredura limitada pode terminar sem itens visíveis, apesar de haver outras páginas.
- Removida a opção Most appearances. Default passou para `date_last_updated:desc`, conferido com resposta real: cinco registros Marvel na primeira página de quarenta, incluindo Hoggoth e Wonder Man (Lerner). A–Z/Z–A continuam disponíveis. Não se promete ranking global por popularidade.
- Cache de páginas atualizado para v3 para não reaproveitar páginas vazias da ordenação anterior; favoritos e histórico não são apagados.
- Uma página filtrada vazia com continuação agora exibe Continue exploring; No results é reservado ao fim da paginação. Varredura permanece limitada para respeitar a API.
- Busca real `spider man` em `/search/` retornou Spider-Man e variantes Marvel. Detalhe `/power/4035-1/` retornou Flight e 8438 referências de personagens. Chamadas com a chave local, sem expô-la em logs.
- Teste adicionado para impedir o envio da ordenação de aparições ignorada; teste de ordenação anterior corrigido. Os testes antigos validavam a construção do parâmetro, mas não demonstravam suporte real pelo provedor.

## Revisão de busca e poderes — 01/10/2026

Continuação da revisão: nova consulta única a Spider-Man ainda retornou HTTP 403/HTML do Cloudflare. Usuário confirmou o erro no celular em Wi-Fi e informou não ter dados móveis para comparação; não foi possível determinar se o bloqueio depende dessa rede.

- Fila de requisições passou de monitor bloqueante para Mutex de coroutines, permitindo cancelar buscas enquanto aguardam acesso ao transporte. Requisições já iniciadas permanecem sujeitas aos timeouts de conexão/leitura.
- Prazo de espera após bloqueio passou a ser informado na mensagem de tentativa antecipada; corrigida comparação de relógio monotônico para aceitar valores negativos.
- Busca global agora consulta caches das demais categorias mesmo quando a primeira chamada é bloqueada, sem iniciar novas chamadas de rede para essas categorias.
- Marvel AI aberto de detalhes reutiliza os dados Comic Vine já carregados, removendo a dependência de uma consulta redundante ao provedor.
- Aviso de fallback descreve conexão indisponível em vez de presumir ausência de internet.
- Sete testes adicionais cobrem cancelamento na fila, Retry-After, recuperação após prazo, falha comum sem bloqueio global, paginação preservando offsets após filtro Marvel, busca parcial com poder salvo, detalhe expirado recuperado do cache e propagação de erro sem dados salvos. São dados simulados exclusivamente nos testes, sem substituição da API no aplicativo.

- Reproduzido HTTP 403 em listagem simples, filtro de nome, `/search/` e `/power/4035-1/`, usando a chave local sem expô-la. Resposta HTML, servidor Cloudflare e conteúdo indicando bloqueio/challenge; não houve resposta JSON `status_code=100` confirmando chave inválida. As evidências de consultas bem-sucedidas abaixo são anteriores a este bloqueio.
- Corrigida classificação de erro: bloqueio HTTP 403, credencial rejeitada e limite HTTP 429 têm mensagens distintas. Não há tentativa de contornar o bloqueio do provedor.
- Busca de personagens/equipes/arcos passou a usar `/search/` com `resources` e `query`, sem ordenação alfabética sobre os resultados de pesquisa. Poderes mantêm seu filtro de coleção.
- Catálogo de personagens inicia por quantidade de aparições; A–Z e Z–A continuam disponíveis. Nomes da Comic Vine não são reescritos nem escondidos arbitrariamente. Cache de páginas versionado para não reaproveitar a estratégia anterior.
- Busca global sequencial, interrompida diante de recusa geral do servidor; transporte serializado e pausa após bloqueio/rate limit para evitar rajadas de requisições.
- Testes de regressão adicionados para construção de busca, paginação, ordenação, filtro de poderes e distinção de falhas. Validação dos resultados reais de Spider-Man e dos personagens associados a poderes continua pendente enquanto o serviço recusar esta conexão.

## Implementado

- Android nativo/Kotlin, MVVM com StateFlow e coroutines; UI, ViewModel, repositories, transporte Comic Vine, modelos e persistência separados.
- Splash do sistema e inicialização automática; onboarding, cadastro/login local com PBKDF2, sessão persistente e logout.
- Home com dados reais, Explore, busca global de quatro tipos, categorias paginadas, filtros/ordenação e detalhes conectados.
- Comic Vine via HTTPS, User-Agent, format=json, field_list, limit/offset e priorização de publisher Marvel.
- Room para contas, favoritos e histórico separados por usuário; DataStore para sessão/onboarding/aparência/idioma.
- Cache em disco, leitura de registros salvos sem rede, loading/erro/vazio, retry e cancelamento lógico de pesquisas antigas.
- Gemini real, chat contextual a partir de detalhes Comic Vine, fontes navegáveis e tratamento de falhas.
- Perfil, edição de nome/avatar, histórico, favoritos, configurações, temas, idioma, About e informações de privacidade.
- Plus Jakarta Sans empacotada com licença OFL e ícones originais exportados do Figma.

## Evidências de validação

- Consulta real Comic Vine: status 1; Wolverine, Avengers, Story Arcs e taxonomia de poderes retornados.
- Gemini: chave aceita, modelo gemini-2.5-flash disponível e generateContent retornou resposta real.
- assembleDebug: APK gerado em app/build/outputs/apk/debug/app-debug.apk.
- testDebugUnitTest: passou (hash/validação de senha e teste básico do projeto).
- connectedDebugAndroidTest: três testes passaram no Medium Phone API 36.1. Verificam autenticação local, rejeição de duplicatas/credenciais inválidas, favoritos após reabrir o banco, isolamento por conta e limpeza de histórico preservando favoritos.
- Último build após os ajustes do Marvel AI: assembleDebug, testDebugUnitTest e lintDebug passaram (BUILD SUCCESSFUL).
- lintDebug: passou sem erros; avisos de versões mais recentes, sugestões KTX/TOML e recursos não utilizados.
- Relatórios HTML disponíveis em app/build/reports/.
- Inspeção manual: Welcome → Cadastro e Login → visitante → Home com imagem real → Wolverine Detail → favorito → Favorites com registro salvo → Marvel AI exibindo Context / Wolverine. Capturas locais em build/qa/. O envio completo pelo formulário do chat também passou: “Quais sao os poderes dele?” gerou resposta em português sobre Wolverine baseada nos registros Comic Vine. Evidências: build/qa/ai-response.xml e ai-response.png.
- A inspeção encontrou e corrigiu o recorte central que escondia rostos e o tamanho da raiz que deslocava a bottom navigation em telas curtas. O APK com as correções compilou e passou no Lint. Captura favorites-final.png confirma a barra no rodapé; sessão de visitante e favorito sobreviveram à reinstalação com dados preservados.
- Character → Power/Healing foi acionado; a falha de conexão mostrou mensagem offline e retry. A listagem de personagens desse poder não pôde ser conferida nessa tentativa.
- O emulador apresentou ANRs do System UI e timeouts do UiAutomator, mesmo com software rendering e 4 GB. Isso limitou a execução manual completa; testes automatizados de persistência/autenticação passaram antes dessas instabilidades.

## Design

Arquivo Figma: fIxAXlsMLwvvgaFZBp6Rqb. Páginas FOUNDATIONS (0:1), COMPONENTS (148:945), SCREENS + PROTOTYPE (148:1002). Foram identificados 57 estados numerados e um exemplo Jakarta.

Contextos estudados: Splash, Welcome, Login, Register, Home, Explore, Search, Characters, Filters, Character Detail, Teams, Power Detail, Story Arcs e Marvel AI inicial (148:1813). Os textos exportados disponíveis estão em docs/figma/. O usuário escolheu Plus Jakarta Sans em substituição à tipografia original.

Tokens: background #171B20, surface #222930, raised #2D3740, primary #B83F42, secondary #A6C4D4, text #F1EFE8, muted #AFBAC2, paper #DCDACF, ink #17212A, border #52616D. Margens 20dp, app bar 68dp, bottom navigation 60dp, botões 52dp, alvos de toque 48dp, raio 4dp. Barras nativas do Android substituem as barras simuladas no desenho.

A cota do conector Figma Starter acabou durante a inspeção e continuou limitada após a troca de conta. Em 01/10 uma consulta adicional permitiu estudar Marvel AI inicial; o próximo pedido voltou a receber limite. Título e quatro sugestões de perguntas foram ajustados ao contexto obtido, com busca Comic Vine antes da geração de resposta. As demais telas foram implementadas com os componentes estudados e o escopo funcional detalhado fornecido pelo usuário. A fidelidade individual de TODOS os 57 estados ainda não está validada.

Grupos que ainda precisam de conferência visual individual no Figma:
- Variações de Character Detail: 148:2295, 148:2354.
- Teams/detail: 148:1646, 148:2389, 148:2507, 148:2533.
- Powers/list/detail: 148:1670, 148:2330, 148:2559, 148:2577, 148:2595, 148:2614.
- Stories/detail: 148:1733, 148:2633, 148:2659.
- Favorites/history: 148:1770, 148:1795, 148:2023, 148:2037, 148:2052, 148:2223, 148:2247, 148:2271.
- AI: 148:2067, 148:2093, 148:2110, 148:2413, 148:2439, 148:2685, 148:2706.
- Profile/settings: 148:1847, 148:1887, 148:1908, 148:1958, 148:2465, 148:2489.
- Search/states: 148:1982, 148:2009, 148:2134, 148:2148, 148:2162, 148:2178, 148:2200.

## Limites explícitos

- Banco remoto preparado em `database/`: migração inicial, permissões de usuário de execução e instruções Aiven/pgAdmin. Em 02/10/2026, 17 testes passaram em PostgreSQL 18.3 via PGlite, incluindo isolamento entre contas e integridade dos dados. Nada foi aplicado na Aiven; backend e integração Android continuam pendentes.

- Contas locais; sem backend de autenticação, recuperação de senha ou sincronização entre aparelhos.
- A API fornece imagens/descrições/relacionamentos conforme disponíveis. Dados ausentes não são inventados. Poderes podem relacionar personagens de outras editoras.
- Filtro Marvel aplicado aos dados retornados; até três páginas brutas por carregamento. Filtro de poder verifica detalhes da página atual.
- Notificações não incluídas por não haver comportamento definido. Chats, favoritos e histórico persistidos localmente por conta (atualização de 02/10/2026).
- Chaves lidas de local.properties ignorado pelo versionamento. Para distribuição pública, um backend deve proteger credenciais compartilhadas.
- Workaround do socket Gradle neste Windows documentado no README.






Conferência final: Marvel AI inicial renderizado no APK atualizado, com quatro sugestões, campo, botão e navegação fixa; captura build/qa/ai-figma-final.png.

## Ajustes de identidade e navegação solicitados pelo usuário

- Imagens originais copiadas sem modificar a arte para drawable-nodpi/marvel_lobby_icon.png e marvel_lobby_logo.png.
- Ícone Android adaptativo usa a imagem completa; Home, Splash e About usam a versão transparente. A marca Marvel Explorer e o símbolo de três barras foram retirados da interface.
- Splash com permanência mínima de três segundos após inicialização local, sem slogan: apenas logo com alpha oscilando entre 1 e 0,45 e Carregando com reticências de um a três pontos. Animações param ao sair da tela e respeitam a opção do sistema de desativar animações.
- BottomNavigationBar preservada entre renderizações, com ícones preenchidos para a aba ativa, peso de fonte 800 contra 500 nas inativas e transição de 240 ms. Detalhes mantêm a seleção da seção pela qual foram abertos.
- Telas de autenticação, Home, Explore, busca e catálogo separadas em subpastas de presentation; uma Activity continua hospedando a navegação.
Verificação em 01/10/2026: reconstrução limpa (:app:clean :app:assembleDebug) concluída com sucesso. Manifest, resources.arsc e ambas as imagens confirmados no pacote; instalação no emulator-5554 concluída com Success. Testes unitários e Lint haviam passado na compilação anterior com o mesmo código. A Splash atualizada apareceu, mas ANRs do Pixel Launcher, process system e System UI impediram concluir a conferência visual da navbar e da fluidez das animações. Capturas em build/qa/brand-*.png; não considerar esta etapa uma validação visual completa.

Validação da revisão de busca: assembleDebug, testDebugUnitTest e lintDebug concluídos com BUILD SUCCESSFUL. Sete testes unitários, zero falhas/erros (quatro testes novos de regressão). Manifest e resources.arsc presentes no pacote gerado. O bloqueio externo HTTP 403 permanece uma limitação de validação em rede.

Validação final da continuação: assembleDebug, testDebugUnitTest e lintDebug concluídos com BUILD SUCCESSFUL após todos os ajustes, incluindo reutilização de contexto no Marvel AI. 14 testes unitários, zero falhas e zero erros. Manifest e resources.arsc conferidos no pacote. Não houve validação online bem-sucedida de Spider-Man/poderes: a única consulta desta continuação ainda recebeu HTTP 403 do Cloudflare.

Validação da correção de Characters: assembleDebug, testDebugUnitTest e lintDebug concluídos com BUILD SUCCESSFUL. 15 testes unitários sem falhas. Requisição com os mesmos campos, parâmetros, User-Agent e Accept do app retornou HTTP 200/status 1, 40 registros brutos e cinco Marvel visíveis. Manifest e resources.arsc presentes. Validação HTTP feita no computador; não foi instalada nem verificada visualmente no celular do usuário nesta etapa.

Validação final de informações/navegação: assembleDebug, testDebugUnitTest e lintDebug concluídos com BUILD SUCCESSFUL após todos os ajustes. 25 testes unitários, zero falhas/erros. Manifest/resources.arsc presentes; verificado no bytecode que Voltar na Splash preserva o estado salvo. Consultas reais verificaram os campos citados acima. A navegação foi validada por testes automatizados de estado; a revisão visual/manual integral no celular não foi executada nesta etapa.


## Remake visual diretamente no Android — 02/10/2026

A pedido do usuário, o visual passa a divergir deliberadamente do Figma original. Paleta de preto/vinho, vermelho e violeta; superfícies arredondadas; botões com gradiente; navbar flutuante com borda, sombra e indicação animada da aba selecionada. A área rolável reserva espaço para a barra e respeita os insets do sistema/teclado.

Home com faixas horizontais de pôsteres reais da API, histórico recente e convite para IA. Explore usa atalhos compactos com ícones e cores por categoria, substituindo o bloco grande de Characters. Catálogos de imagens usam duas colunas; poderes continuam em linhas compactas. Pôsteres têm texto sobre degradê para legibilidade. A posição horizontal das faixas é preservada durante atualizações da tela. Componentes comuns atualizam autenticação, perfil, favoritos e configurações; detalhes e chat também receberam ajustes.

Sem dispositivo/emulador conectado nesta etapa: aparência, recortes de imagem e fontes ampliadas precisam de conferência visual no Android. A revisão não altera a integração Comic Vine/Gemini nem conecta PostgreSQL.


## Refinamentos após os prints do usuário — 02/10/2026

- Home e Explore deixam 24 dp entre o título e a busca, sem modificar o espaçamento da busca no início de catálogos.
- Power Detail apresenta um único título; o painel duplicado com estrela foi removido. Marvel AI não tem mais o painel decorativo superior. As linhas de poderes usam um raio.
- Character Detail mostra aparições em edições, poderes distintos, equipes relacionadas e arcos relacionados, além de origem e aliases quando disponíveis. São contagens do catálogo, não notas de força/inteligência. Campos ausentes aparecem como traço; um zero só é mostrado quando a API forneceu o campo ou um snapshot possui informação positiva conhecida.
- Comparação: no detalhe, tocar em Comparar com outro personagem, escolher o segundo no catálogo e visualizar as contagens e barras de ambos no detalhe. A escala de cada par é compartilhada. A seleção é temporária no ViewModel, é limpa no logout e pode ser removida pela ação Limpar comparação. Não é restaurada após morte do processo.
- Relações de edições e primeira aparição recebem metadados em lote via /issues/?filter=id:..., com série e número como identificação e título da história como informação secundária. Nomes ausentes não são substituídos por números de edição inventados. Texto ocupa no máximo duas linhas por campo e os links continuam navegáveis durante loading/erro. Carregamento inicial limitado a oito relações mais a primeira aparição; novas relações são buscadas ao tocar Carregar mais. Cache de metadados tem a mesma validade de dez minutos e pode ser usado offline.
- Detalhes usam cache v3; cache v2 antigo ainda é fallback offline. Snapshots de favoritos/histórico antigos permanecem legíveis.

Consulta real no computador confirmou Spider-Man (1443), campos de aparições/poderes/equipes/arcos e relações de edições sem nome. Uma consulta em lote de três IDs retornou The Amazing Venom #1, The Amazing Spider-Man #1000 e Spider-Man/Hulk: Fire and Brimstone #2, confirmando o preenchimento pelo volume/número. Não foram inventadas classificações oficiais de combate. Nenhuma atualização foi instalada no celular do usuário nesta etapa.

Validação desta revisão: compileDebugKotlin, testDebugUnitTest e lintDebug concluídos com BUILD SUCCESSFUL. 31 testes unitários, zero falhas/erros, incluindo contagens ausentes versus zero, registros duplicados, escala da comparação, resolução em lote e cache offline das edições. Conferência visual da versão nova no celular ainda pendente.


## Perfil, conta, idiomas e Marvel AI — 02/10/2026

- Logo Home alinhada à esquerda. Paleta de vermelho/escarlate, superfícies claras quentes, contraste de texto e bordas ajustados. Avatar circular da conta aparece no cabeçalho.
- Galeria de oito imagens originais Comic Vine, obtidas por IDs reais de personagens Marvel e incluídas nos assets com suas fontes. Seleção por carrossel e prévia circular, pelo lápis no perfil. Upload arbitrário/crop de foto pessoal foi substituído pelo seletor aprovado, conforme a decisão final do usuário.
- Perfil apresenta avatar, estatísticas locais e atalhos; edição de conta está concentrada em Configurações, sem dois atalhos Edit Profile no mesmo fluxo. Sair é uma ação vermelha no final de Configurações, com confirmação.
- E-mail mascarado, campos de senha ocultos e ações de olho. Credencial do aparelho solicitada pelo BiometricPrompt nativo com DEVICE_CREDENTIAL quando KeyguardManager informa bloqueio configurado. Sem bloqueio, a ação segue diretamente. Nenhuma senha armazenada é recuperável: a interface mostra apenas os valores digitados nos campos de senha. E-mail/senha exigem senha atual da conta; PBKDF2 com novo salt na troca de senha. E-mail normalizado/único e confirmação verificados. A mudança de e-mail move a biblioteca e conversas numa transação Room.
- Room v2 tem entidade de conversas e migração explícita de v1. Históricos locais isolados por conta, com mensagens/contexto/fontes persistidos, reabertura, exclusão e recuperação de envio interrompido. UUID de conversa não pode ser sobrescrito por outra conta. Históricos antigos em memória não são recuperáveis depois de encerrados.
- Marvel AI usa tema escuro e gradiente próprios, inclusive sob preferência clara. Navbar omitida na conversa. Compositor fixo fora da área rolável; transcript pode atualizar sem substituir o campo/raiz e romper a conexão com a IME. Cabeçalho oferece histórico e nova conversa. Três pontos animados param ao destacar a view e respeitam animações desativadas.
- Português cobre seções antes omitidas, conta, preferências, erros comuns e demais textos novos. Descrições têm visão geral e páginas de até 1.200 caracteres, sem descartar o restante; anteriores/próximas partes acessíveis. Português é obtido pelo Gemini por trecho e armazenado em cache por hash do conteúdo. Original e tentativa novamente disponíveis; falha mantém texto original. Tradução é identificada como IA. Nomes próprios oficiais são preservados.

Evidências: consulta real de oito personagens e download das imagens originais; Gemini respondeu uma tradução curta em português com finishReason STOP. Cinco testes nativos de persistência/migração/credenciais passaram. Alertas de ANR da System UI do emulador cobriram as primeiras capturas e bloquearam o foco/teclado. Após fechar o alerta no emulador temporário, os dois testes de interface passaram, incluindo o compositor inteiro acima da IME após atualizações de estado, sem navbar. Capturas do tema claro, perfil e seletor foram conferidas. Essa conferência identificou o lápis parcialmente cortado; sua posição foi corrigida e recebeu uma verificação explícita de visibilidade. Compilação, 34 testes unitários e lint concluídos sem erros; o lint contém avisos sobre dependências/recursos existentes. Nenhuma instalação foi feita no celular conectado. A confirmação por PIN/senha em um aparelho com bloqueio configurado não foi exercitada manualmente.

Execução final: BUILD SUCCESSFUL, 34 testes unitários sem falhas e os dois testes de interface passaram novamente após a correção do lápis. Total de sete testes nativos distintos aprovados, somando os cinco de armazenamento. Capturas finais em build/qa/profile-remake mostram o seletor, o avatar/lápis inteiro no perfil e a mensagem acima do teclado efetivamente desenhado. Emulador temporário executado com -read-only, sem instalação no celular do usuário.

## Traduções com Groq — 02/10/2026

- Traduções migradas do Gemini para um cliente remoto dedicado da Groq. Marvel AI continua no Gemini; o método de tradução foi removido de AiRepository, sem fallback para Gemini.
- GROQ_API_KEY vem apenas de local.properties para BuildConfig. Modelo opcional GROQ_TRANSLATION_MODEL, padrão openai/gpt-oss-20b, com reasoning_effort low. Apenas o trecho do catálogo é enviado, sem dados da conta ou conversas.
- Cache pt-v1 existente preservado. Consultas concorrentes iguais reutilizam a tradução salva; apenas uma tradução sem cache é enviada por vez. Cancelamentos são verificados antes de enviar e antes de salvar. Respostas incompletas/vazias e erros não entram no cache. Texto original/tentativa novamente permanecem disponíveis.
- Preferências, Sobre e Privacidade atualizados em inglês e português para identificar corretamente a Groq como provedora de tradução.
- Consulta real de modelos com a chave configurada não disponibilizou os Llama testados; openai/gpt-oss-20b está disponível. Uma tradução curta retornou HTTP 200 e finish_reason stop com texto em português. Nenhuma chave foi exibida nos logs.

Documentação de referência: https://console.groq.com/docs/api-reference e https://console.groq.com/docs/models. Testes novos cobrem cache após reiniciar/trocar provedor, deduplicação concorrente, erro/retry, conteúdo Unicode e respostas completas versus truncadas/vazias/inválidas.

Validação final: compileDebugKotlin, testDebugUnitTest e lintDebug concluídos com BUILD SUCCESSFUL. 42 testes unitários, zero falhas/erros. BuildConfig gerado confirmou o modelo openai/gpt-oss-20b. Tradução real com a chave local passou; nenhuma instalação foi feita no celular do usuário nesta etapa.

## Perfil e transições da IA — 05/10/2026

- Perfil mostra nome e @username; e-mail e o botão para revelá-lo foram removidos dessa tela. O e-mail permanece mascarado na edição da conta em Configurações.
- Username persistido no Room v3, com migração 2→3 que gera identificadores a partir do nome público e um sufixo aleatório para contas existentes. Índice único local; edição/normalização em Configurações → Editar perfil. Trocar nome/e-mail preserva o username. Mudança de e-mail atualiza a linha existente e move os registros/conversas na mesma transação, preservando o índice único.
- Entrada/saída de Marvel AI mantém a tela anterior durante um fade com deslocamento de 300 ms; a navbar desaparece/reaparece junto com a tela. Atualizações da mesma rota mantêm a camada animada. Tela anterior não recebe toques ou foco de acessibilidade. Cancelamento de transições e destruição da Activity removem as camadas antigas; animações desativadas no sistema são respeitadas.
- Nova conversa usa vetor de adição centralizado num botão de 48 dp, igual ao histórico; remove o desalinhamento causado pelo botão de texto com altura mínima maior que o espaço do cabeçalho.

Validação final: compilação e lint passaram sem erros; 44 testes unitários e 10 testes nativos aprovados, sem falhas. Migrações 1→2→3 e 2→3 preservaram contas/registros/conversas; usernames persistiram após reabrir o banco e alterações duplicadas foram rejeitadas sem modificar a outra conta. Testes de interface confirmaram o @username sem e-mail no perfil, ícone de nova conversa centralizado/funcional, conclusão da transição com uma única camada e retorno da navbar, além do compositor acima do teclado após atualizar o estado. Capturas profile-light, ai-header e ai-keyboard em build/qa/profile-remake conferidas visualmente. Testes realizados em emulador temporário somente leitura, sem instalação no celular conectado.

## Camada social — preparação do PostgreSQL — 05/10/2026

Arquitetura atual analisada e proposta registrada em docs/SOCIAL_PLAN.md. O usuário pediu preparar primeiro os scripts para instalar o banco na Aiven. Nenhuma autenticação Android foi substituída nesta etapa.

- 000_setup_phase1.sql reúne o esquema inicial e a migração de identidade social para execução única em banco novo pelo pgAdmin. Não é reset, não altera o schema public nem inclui senhas. Para esquema inicial existente, usar apenas 003_social_identity.sql.
- Username global único/normalizado, bio de até 280 caracteres, avatar aprovado e view de perfil público sem dados de autenticação. Privacidade de favoritos/atividade inicialmente desativada para compartilhamento.
- Hashes de access token e expiração acrescentados às sessões; futura implementação do backend controlará verificação, rotação e revogação. Conversas/mensagens existentes permanecem específicas do Marvel AI.
- 002_runtime_permissions.sql passou a conceder somente SELECT na projeção pública e na versão da estrutura, mantendo restrições do usuário da API. Configuração local database/.env preparada a partir do modelo; credenciais permanecem fora do Android.

Validação: 17 testes PostgreSQL existentes + 13 testes novos passaram em PGlite/PostgreSQL 18.3, incluindo instalação como migrador sem superprivilégios, atualização preservando dados, isolamento de perfis privados e proteção da view pública. Não foi feita conexão, instalação ou publicação na Aiven. Backend e integração remota ainda não foram implementados; dependem da próxima etapa.

## Identidade online — Fase 1 — 05/10/2026

- Backend Node/TypeScript/Fastify implementado em backend/. Cadastro/login, @username global, bio/avatar aprovados, perfil privado e perfil público autenticado sem e-mail/senha. UUID emitido pelo servidor permanece ao editar e-mail/username.
- Senhas scrypt com salt (N=131072, r=8, p=1), concorrência limitada a duas operações; access token de 15 minutos e refresh de 30 dias com rotação por família, detecção de reutilização e revogação. Banco guarda hashes dos tokens. Alterações de credenciais exigem senha atual e revogam todas as sessões anteriores; perfil e credenciais podem ser atualizados atomicamente.
- SQL parametrizado, RLS com contexto transacional, validação estrita, erros controlados sem SQL/segredos, limite de tentativas e IP encaminhado não confiado por padrão. Pool máximo cinco conexões.
- Usuário instalou os scripts em marvel_mobile/Aiven e executou permissões para marvel_lobby_api. Verificação real somente de leitura passou: TLS/hostname/CA, função restrita, esquema e view pública. API iniciou localmente e respondeu /health. Não foram usadas credenciais administrativas nem criados usuários de teste na Aiven.
- Android: transporte exclusivamente HTTPS, repository remoto, tokens AES-GCM/Android Keystore, cache de perfil no Room e seleção de sessão no DataStore. LOBBY_API_BASE_URL vem de local.properties; ausente mantém acesso local, sem apresentar botões online. Nenhuma senha PG entra no Android.
- Room v4 acrescenta perfis remotos e associações de contas. Fluxo explícito confirma senha local e autentica/cria conta remota antes de associar em transação. Favoritos são mesclados, histórico mantém a data mais recente, conversas de IA são preservadas no aparelho e escritas antigas seguem a associação. Uma conta local não pode ser reassociada a outro UUID. Não é sincronização em nuvem nem chat privado.
- Telas de acesso distinguem conta online/local; Meu Perfil e Editar perfil suportam bio/data de entrada e avatar existente; novas telas Conectar conta online e Perfil público usam componentes atuais e tradução PT. E-mail continua só na edição protegida da conta.
- Usuário escolheu Render gratuito. render.yaml, backend/.node-version, Dockerfile opcional e docs/RENDER_SETUP.md preparados, sem publicar dados/secrets. Ainda faltam repositório/hospedagem pública e configuração da URL HTTPS. Docker não foi executado; fluxo real Android→HTTPS público permanece pendente. Fases 2–4 não foram implementadas nesta etapa.

Validação do backend: 12 testes passaram usando API completa, scrypt real e PostgreSQL temporário PGlite, incluindo rollback combinado de perfil/credenciais, autorização/perfil público, tokens e rate limit. npm audit das dependências de produção não encontrou vulnerabilidades conhecidas. Compilação Android e 46 testes unitários passaram; lint sem erros (60 avisos de dependências/recursos).

Validação nativa: 15 testes distintos passaram no emulador temporário Small_Phone, -read-only, sem instalação no celular. Os cinco testes novos verificam migração Room 3→4, mesclagem/associação idempotente e sem reassociação, preservação e proteção de chats de IA, redirecionamento de escritas antigas, cifragem/isolamento dos tokens e renovação de sessão sem apagar login posterior. Os dez testes anteriores confirmaram contas/migrações 1→4 e 2→4, biblioteca isolada, avatar, teclado e transições. A compilação/lint final também passaram após ajustar a revogação de uma sessão retirada cujo access token já expirou. Backend local temporário encerrado após o health check.

O teste de renovação/logout foi executado novamente após o ajuste final e passou: uma sessão retirada com access token expirado é renovada apenas para revogar sua própria família, sem sobrescrever os tokens de um login posterior. Emulador temporário encerrado ao terminar a validação. Nenhum repositório remoto foi criado e nenhuma publicação foi realizada.

## Ativação da API pública — 05/10/2026

O código foi publicado em https://github.com/BrenoLeal4sl-dev/Marvel-Lobby, preservando a licença inicial e excluindo credenciais/arquivos locais. O usuário publicou o serviço Docker no Render gratuito. A construção da imagem passou; o primeiro início falhou por senha PostgreSQL incorreta (28P01). Após o usuário corrigir a configuração privada no Render, o serviço iniciou.

Verificação pública de leitura: https://marvel-lobby-api.onrender.com/health retornou status ok e serviço Marvel Lobby; GET /v1/me sem sessão retornou HTTP 401. Nenhuma conta de teste foi criada na Aiven. LOBBY_API_BASE_URL foi configurada no local.properties ignorado deste ambiente, mantendo as outras propriedades. assembleDebug passou com a URL injetada no BuildConfig. Nenhuma instalação foi realizada no celular.

A próxima execução pelo Android Studio oferece acesso online e associação explícita da conta local pelo perfil. Cadastro/login e associação no Android contra a hospedagem pública ainda não foram testados de ponta a ponta; testes anteriores validaram esses componentes com ambiente isolado. Dados de favoritos/histórico/chats permanecem no aparelho. Fases sociais 2–4 continuam pendentes.

## Recusa ao paginar equipes — 05/10/2026

O usuário recebeu HTTP 403 ao carregar mais equipes. Uma consulta de leitura independente a /teams/, offset 40 e limit 40, também retornou HTTP 403 sem Retry-After neste computador. Isso confirma uma recusa atual do serviço externo; não confirma credencial inválida nem garante que seja um problema exclusivo do Wi-Fi do celular.

O transporte agora espera pelo menos um segundo após cada consulta antes de abrir a próxima, inclusive nas páginas percorridas pelo filtro Marvel. A espera é suspensa e cancelável. A primeira recusa fornece o prazo mínimo de pausa de 30 segundos ao ViewModel, assim como tentativas durante a pausa. A listagem mantém itens e offset anteriores em falhas; quando já há itens, mostra um aviso específico de paginação. O botão de nova tentativa fica desabilitado com contagem regressiva e é atualizado sem reconstruir a listagem ou alterar sua rolagem; callbacks são removidos ao sair da tela. O app não tenta contornar a recusa e não troca a chave automaticamente. A disponibilidade de novas páginas ainda depende da Comic Vine.

Validação: compilação e lint passaram; 49 testes unitários passaram sem falhas, incluindo três regressões novas para espaçamento entre consultas bem-sucedidas, cancelamento durante a espera e prazo da primeira recusa com bloqueio de novas chamadas prematuras. Não foi feita validação visual no aparelho nesta revisão.

## Nomes em português no Marvel AI — 05/10/2026

Identificado que a busca anterior enviava "foi homem aranha" ao catálogo ao receber "Quem foi o homem aranha?". AiCatalogQuery agora identifica nomes comuns de personagens/equipes/poderes/arcos em português e inglês, normalizando acentos, espaços e hífens. São aliases de busca, sem IDs, descrições ou estatísticas inventadas. O texto original da pergunta permanece no chat. Nomes não mapeados continuam pela busca geral, removendo palavras de pergunta, incluindo o passado "foi/era/was".

AiContextRepository concentra a resolução e recuperação dos dados fora da UI: consulta o recurso correto, prefere o nome exato e carrega seus detalhes. Reaproveita registros já disponíveis em perguntas como "quais são os poderes dele?" e "e os poderes?", inclusive após reabrir uma conversa. Uma menção explícita pode trocar o assunto de um contexto selecionado; sugestões iniciais não fixam o assunto para sempre. Se o detalhe falhar, mantém o resumo recebido; cancelamento não vira sucesso vazio.

A instrução do Gemini reconhece nomes localizados e distingue aliases de busca de evidências do catálogo. Quando não houver registros, pede resposta com conhecimento complementar claramente identificado, sem alegar uma consulta bem-sucedida. Não há chamada extra ao Gemini/Groq para traduzir os nomes. A tabela cobre nomes comuns e não pretende ser um catálogo exaustivo. Consultas Comic Vine ainda podem receber o bloqueio externo descrito acima.

Validação: assembleDebug e lint passaram; 60 testes unitários passaram sem falhas. Os 11 testes novos cobrem nomes localizados/acentos/hífens, múltiplos assuntos, limites de palavras, nomes genéricos, limpeza de perguntas no passado, recuperação de personagem exato, continuidade sem novas consultas, troca explícita de assunto, resumo preservado após falha no detalhe, indisponibilidade do catálogo e cancelamento. Não foi enviado um pedido real ao Gemini nem testado o chat no aparelho nesta revisão; as respostas do modelo ainda precisam ser conferidas na execução do app.

## Resposta direta quando o catálogo está indisponível — 05/10/2026

O usuário confirmou por captura que o nome já era reconhecido, mas o Gemini respondia somente com uma oferta de consultar conhecimento suplementar. AiResponsePolicy agora orienta resposta substantiva no mesmo turno, sem pedir permissão, com uma nota curta sobre conhecimento complementar quando faltam referências. Ausência de registros fornecidos não autoriza afirmar que o personagem não foi encontrado ou não existe na Comic Vine. Pedidos antigos de permissão presentes no histórico são identificados como comportamento a corrigir, não como uma regra para a conversa.

Validação real: duas chamadas curtas ao Gemini configurado localmente, usando a política efetivamente compilada do aplicativo, sem registros Comic Vine. "Quem foi o homem aranha?" recebeu uma explicação direta em português sobre Peter Parker, concluída normalmente, com nota de conhecimento suplementar. O caso com a resposta antiga pedindo permissão no histórico também respondeu diretamente, sem repetir essa confirmação. Não foi usada a Groq nem enviada informação de conta; chaves permaneceram privadas. A compilação e o lint passaram. Não foi executado o chat no aparelho; é necessário executar a versão atualizada e enviar novamente a pergunta. Mensagens já armazenadas não são reescritas.


## Comunidade, seguidores e mensagens — 05/10/2026

O pedido atual substitui solicitações de amizade por seguir/seguidores. `004_community.sql` entregue e confirmado executado pelo usuário em `marvel_mobile`. Migração versão 3 é idempotente e preserva dados anteriores. Tabelas de chat direto são distintas da IA; RLS impede acesso de terceiros e falsificação do remetente/leitura.

Backend e Android implementam busca de pessoas por nome/@username com paginação, bio e perfil público, contadores/listas de seguidores e pessoas seguidas, seguir/deixar de seguir, conversa única por par, caixa de entrada, não lidas e leitura, histórico paginado, envio com UUID idempotente e sinais autenticados por WebSocket. O chat preserva o campo nativo durante atualizações, mantém o compositor acima do teclado e recupera novidades ao reconectar. Home/Perfil abrem a Comunidade sem alterar os quatro destinos principais. Textos novos traduzidos PT/EN; identidade social independe da Comic Vine.

Validação: 21 testes de backend passaram com scrypt real/PostgreSQL temporário, incluindo busca sem e-mail, filtros literais, unilateralidade, RLS, par único, reenvio, paginação, leitura monotônica, contagem de não lidas e sinais só para participantes. Bloqueio transacional por conversa ordena alocação/commit dos IDs de envio. Android compilou; lint passou; 66 testes unitários passaram. Um teste nativo no emulador `Small_Phone` passou, verificando que o campo não é recriado ao atualizar a tela e permanece totalmente visível acima do teclado sem navbar. Identidade sintética local sem tokens, sem criar contas ou mensagens na Aiven. Não houve instalação no celular.

A primeira consulta somente de leitura à Aiven passou após a confirmação do usuário; duas tentativas posteriores de conferir versões/permissões terminaram com timeout de conexão deste computador. Isso não altera a migração já executada. Publicação e verificação HTTPS da revisão da comunidade serão registradas abaixo quando concluídas. O teste completo entre duas contas reais pelo celular ainda depende da execução da versão atualizada pelo usuário.

Limites: mensagens diretas guardadas no servidor, sem criptografia ponta a ponta, anexos, push, feed, bloqueio/moderação ou envio offline persistente. Rascunhos/cache sociais ficam em memória; histórico de IA continua no Room. Realtime usa uma instância e pode demorar após suspensão do plano gratuito.
