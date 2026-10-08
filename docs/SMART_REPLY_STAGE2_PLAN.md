# Smart Reply: preparação da comparação Gemini

Objetivo: estender somente o avaliador existente e validar offline os cinco perfis solicitados. Sem commit, chamadas Gemini ou deploy.

Arquitetura: exportação Java do prompt real preservada; runner REST Node isolado; perfis e preços explícitos; aprovação vinculada ao plano. Sem SDK novo nem alterações em serviços Java.

- [x] Inspecionar Git, serviços, exportador, casos e documentação Google.
- [x] Adicionar testes dos perfis, custo, amostragem e aprovação; observar falha antes da implementação.
- [x] Implementar `profiles.mjs`, ajustar `benchmark.mjs` e extrair `runner.mjs` para controle CLI.
- [x] Verificar mocks: identidade de prompt, retries, erros, truncamento, consumo ausente, parser e CLI sem rede.
- [x] Executar exportação/API/build offline, Node, dry-run e whitespace.
- [x] Documentar compatibilidade, rubrica humana, projeções, comandos futuros, evidências e estado Git.

Revisão: aprovação obsoleta, limite com retries, thinking sem metadados, respostas partidas e saídas vazias/MAX_TOKENS. Todos devem produzir métricas ou interromper com segurança. Preflight confirma somente catálogo/capacidades; aceitação dos parâmetros depende da execução futura autorizada.
