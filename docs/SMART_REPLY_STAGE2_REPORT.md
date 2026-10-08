# AtendeAI Manager — preparação Gemini do Smart Reply, etapa 2

08/10/2026. **Preparação offline concluída; avaliação real pendente de autorização específica. Zero requisições ao Gemini, nenhum deploy, commit, push, merge, publicação ou mudança de versão.**

## Resultado

O benchmark existente foi estendido para os cinco perfis solicitados, com os mesmos 30 casos e prompt exportado do fluxo Java real. REST v1 e limites interativos permanecem no comparativo principal. Novos modelos recebem thinking explícito e não recebem amostragem descontinuada; a referência mantém o corpo original.

| Perfil | Modelo confirmado | Parâmetros | Compatibilidade documental | Validação na conta |
|---|---|---|---|---|
| Referência | `gemini-2.5-flash-lite` | 512 tokens; temperatura 0,3; sem thinking adicional | Texto / generateContent / v1 | Não executada |
| Minimal | `gemini-3.5-flash-lite` | 512; `thinkingConfig.thinkingLevel: MINIMAL`; sem temperatura | Texto / generateContent / v1 | Não executada |
| Low | `gemini-3.7-flash` | 512; `thinkingConfig.thinkingLevel: LOW`; sem temperatura | Texto / generateContent / v1 | Não executada |
| Low | `gemini-3.8-flash` | 512; `thinkingConfig.thinkingLevel: LOW`; sem temperatura | Texto / generateContent / v1 | Não executada |
| Medium | `gemini-3.8-flash` | 512; `thinkingConfig.thinkingLevel: MEDIUM`; sem temperatura | Texto / generateContent / v1 | Não executada |

Fontes oficiais: [versões v1/v1beta](https://ai.google.dev/gemini-api/docs/api-versions), [schema generateContent](https://ai.google.dev/api/generate-content), [thinking e consumo](https://ai.google.dev/gemini-api/docs/thinking), [mudanças de amostragem](https://ai.google.dev/gemini-api/docs/whats-new-gemini-3.6), [3.8/migração](https://ai.google.dev/gemini-api/docs/latest-model), [modelo 3.7](https://ai.google.dev/gemini-api/docs/models/gemini-3.7-flash), [modelo 3.5 Lite](https://ai.google.dev/gemini-api/docs/models/gemini-3.5-flash-lite), [preços](https://ai.google.dev/gemini-api/docs/pricing). Consultadas em 08/10/2026; a tabela HTML de versões marca thinking como disponível em v1 e v1beta. A skill instalada `gemini-skills:gemini-api-dev` foi consultada; prevalece a instrução do usuário de manter 2.5 como referência e não migrar produção.

Não foi usado o esquema Interactions `generation_config.thinking_level` no corpo generateContent. Não há Pro, áudio/TTS, julgador IA, SDK novo ou troca automática de endpoint/modelo. O acesso real na conta e a aceitação dos parâmetros exigem preflight e primeiras gerações da amostra futura, já dentro do número autorizado de chamadas.

## Arquivos desta tarefa

| Arquivo | Responsabilidade |
|---|---|
| `eval/smart-reply/benchmark.mjs` | Perfis por geração, consumo real/reconciliação/cache, incompletas, parser, latência e métricas humanas |
| `eval/smart-reply/benchmark.test.mjs` | Testes anteriores adaptados ao novo conjunto e novos cenários |
| `eval/smart-reply/profiles.mjs` | Cinco configurações, dois períodos de preços, amostra, reservas, projeções e hash de aprovação |
| `eval/smart-reply/profiles.test.mjs` | Paridade de conteúdo, custos, limites e autorização |
| `eval/smart-reply/runner.mjs` | CLI offline por padrão, aprovação vinculada, contadores e persistência gradual |
| `eval/smart-reply/runner.test.mjs` | Fluxo completo com transporte mockado; parada por quota/consumo inesperado |
| `eval/smart-reply/README.md` | Matriz, parâmetros, limites, rubrica, preços/projeções e comandos PowerShell |
| `eval/smart-reply/results-template.csv` | Cinco linhas para preenchimento posterior, 34 colunas |
| `docs/SMART_REPLY_STAGE2_PLAN.md` | Plano/checklist técnico da preparação |
| `docs/SMART_REPLY_STAGE2_REPORT.md` | Este relatório |

Os quatro primeiros arquivos relevantes da etapa 1 foram inspecionados. `SmartReplyService.java`, `GeminiService.java`, os testes Java anteriores, `cases.json` e `SMART_REPLY_STAGE1_REPORT.md` foram preservados. Nenhum arquivo de produção desta tarefa foi editado. Nenhum arquivo da extensão foi alterado.

## Evidências offline

| Verificação | Resultado |
|---|---|
| Testes de perfis antes da implementação | Falha esperada por módulo ainda ausente |
| Testes Node do benchmark/perfis/runner | **24 passaram; 0 falhas/skips** |
| API: Maven 3.9.11, Java 21.0.10, `-o -DsmartReply.export=true verify` | **92 testes; 0 falhas/erros/skips; BUILD SUCCESS** |
| Compilação e empacotamento API | Passaram no mesmo `verify` |
| Exportação Java | 30 casos; transporte interceptado por MockRestServiceServer |
| Suíte existente da extensão, na raiz da extensão | **289 passaram; 0 falhas/skips** |
| `node --check` em todos os `.mjs` do avaliador | Passou |
| Dry-run: amostra 3, completo 30, preços posteriores | Executados, sem acesso de rede |
| `git diff --check` dos dois repositórios | Passou |
| Whitespace dos novos arquivos, que ainda estão untracked | Verificado separadamente, sem problemas |

Avisos preexistentes de instrumentação dinâmica Java/Mockito não impediram o build. A primeira invocação da suíte da extensão a partir da raiz da API falhou por caminhos relativos (`ENOENT`); a execução na raiz correta passou com 289 testes. Isso não exigiu alteração de código.

Logs locais de API/Node e bundles/dry-runs ficam em `target/`, ignorado. Os testes do caminho `--paid` usam transporte explicitamente injetado, respostas sintéticas e chave dummy; não exercitam o transporte HTTPS real. A CLI comum e dry-run permanecem offline mesmo com chave/autorização presentes.

SHA-256 dos requests exportados: `e6c52dc937a4544c555b15d30dd6feeb45e7dee4463b48438c2b722b76dfdee6`.

## Dry-run e execução futura

Trecho real, abreviado:

```json
{"mode":"offline","cases":3,"networkCalls":0,
 "caseIds":["03-established","16-no-technical-evidence","27-long-bounded"],
 "logicalGenerations":15,"maxGenerationCalls":30,"metadataCalls":4,
 "maxHttpCalls":34,"budgetUsd":3,"reservedUsd":0.1918105,
 "maxOutputTokens":512,"period":"promo","experiment":"main"}
```

| Fase | Máximo HTTP | Reserva até 31/12/2026 | Reserva desde 01/01/2027 |
|---|---:|---:|---:|
| Amostra 3 × 5 | 34: até 30 POST + 4 GET | US$ 0,191811 | US$ 0,352321 |
| Completo 30 × 5 | 304: até 300 POST + 4 GET | US$ 1,149939 | US$ 2,102827 |
| Amostra seguida de execução completa independente | 338 | **US$ 1,341749** | **US$ 2,455149** |

Teto proposto total: **US$ 3**. Reserva conservadora com dois POST por geração, entrada de bytes UTF-8 + 512 para enquadramento e saída total limitada a 512, inclusive thinking; cache não reduz reserva. A estimativa local não é garantia de cobrança do provedor. Consumo desconhecido, timeouts e retries ocupam reserva integral; ultrapassagem observada impede gerações posteriores. Não foi feito `countTokens` autenticado. Sem impostos, câmbio ou infraestrutura.

Os preços Standard oficiais em USD/milhão: referência 0,10/0,40; 3.5 Lite 0,30/2,50; 3.7/3.8 promocionais 0,75/3,75 e posteriores 1,50/7,50 (entrada/saída, esta incluindo raciocínio). Não confundir pensamento com texto visível. Metadados reais são prioritários; ausência ou inconsistência produz custo desconhecido, jamais gratuidade presumida.

Os [comandos completos PowerShell](../eval/smart-reply/README.md#comandos-powershell) definem `--paid`, `SMART_REPLY_EVAL_AUTHORIZED=YES`, `SMART_REPLY_EVAL_APPROVED_PLAN` com hash exato, `--max-calls`, `--budget-usd` e saída nova. Os comandos não são autorização. Entre as fases, interromper para avaliar qualidade, falhas, uso e orçamento, e obter nova aprovação.

A amostra pequena inclui continuidade, falta de evidência e conversa longa. O completo inclui os mesmos 30 casos originais, com uma geração lógica por perfil/caso e ordem dos perfis rotacionada. Os três casos iniciais se repetem na fase completa independente, explicitamente incluídos na soma. Não há retomada ou repetição paga automática. Se o plano futuro ultrapassar US$ 3, reduzir amostra/perfis e pedir autorização para outro hash. Diagnóstico com `--diagnostic --max-output-tokens 2048` requer autorização própria e arquivo separado; não entra no comparativo principal.

## Qualidade, tabela e escolha

O [CSV preparado](../eval/smart-reply/results-template.csv) separa os cinco perfis e inclui tokens, custos por resposta/100/1.000, projeções diárias/mensais, técnicos, latência média/mediana/p95, erros/timeouts/incompletas/MAX_TOKENS, parser e quatro categorias humanas. Todas as células de resultados permanecem vazias, pois não houve avaliação real.

Rubrica: ready (uso com revisão mínima), adjust (correta com ajustes), generic (pouco útil), incorrect (incorreta/inventada). Revisão humana cega ao modelo, com observações e falhas críticas. Avaliar última pergunta, etapa/tentativas, clareza, assertividade com evidência, ausência de invenção, contexto incompleto, perfil e adendo. Regex e testes de estrutura são verificações objetivas; não substituem avaliação semântica. Respostas longas não recebem vantagem automática.

Projeções no manual declaram 22 dias úteis/mês, 5/10/20 sugestões por dia e 1/5/10/20 técnicos. Os resultados reais agregam todos os retries; o exemplo financeiro é identificado como hipotético. A decisão deve priorizar zero falhas críticas e maior utilidade/menor edição, depois completude/p95 sob as restrições atuais e custo por resposta ready, incluindo o preço de 2027. Não há evidência para recomendar troca de modelo agora.

## Limitações e proposta isolada de backend

- Limite 512 pode ser consumido pelo thinking, especialmente Medium; medir saídas vazias/MAX_TOKENS. Perfil que só funciona com limite maior não atende ao comparativo principal.
- Leitura de 4 s pode inviabilizar thinking mesmo com ganho de qualidade. A latência Node→Gemini exclui Cloud Run, fila, cold start e salto da extensão.
- Catálogo/documentação não garantem acesso na conta, capacidade de parâmetros nem quota. O preflight futuro valida metadados, e a primeira geração do próprio lote valida o corpo; sem fallback silencioso.
- O parser atual lê só a primeira parte. Divergência para todas as partes visíveis é registrada e reprova a verificação determinística; não foi corrigida em produção.
- Timeout pode ter cobrança sem metadados; a fatura precisa ser reconciliada. Trinta casos e uma passagem são evidência preliminar, sem significância estatística.

Proposta futura, somente documental: configuração específica de modelo e thinking do Smart Reply; remoção de parâmetros por família; parser de partes textuais sem `thought`; tratamento de finishReason; metadados seguros de uso e avaliação do prazo interativo. Não trocar a variável global `GEMINI_MODEL`, nem endpoints, revisão Cloud Run, contrato HTTP, Relatório/Docs/classificação CRM. Nenhuma proposta foi implementada/ativada nesta etapa.

## Estado final do Git e preservação

API: branch `main`, HEAD `4b19537`. Continuam a modificação local anterior em `SmartReplyService.java` e arquivos untracked da etapa 1 (`SMART_REPLY_STAGE1_REPORT.md`, `eval/`, testes de privacidade/exportação/continuidade). Esta tarefa acrescenta plano/relatório da etapa 2 e atualiza/acrescenta apenas os arquivos de avaliador/documentação listados acima. Nada foi staged.

Extensão: branch `main`, HEAD `8255c80`; mesmo estado inicial: 14 arquivos modificados, `dist/` e três testes novos untracked. A lista exata de modificados é `content.js`, `modules/chat-capture.js`, `modules/messages.js`, `modules/smart-reply.js`, `modules/theme.js`, `modules/ui-builder.js`, `styles/modals.css`, `styles/smart-reply.css`, `styles/tokens.css`, `tests/css-isolation.test.js`, `tests/recovery-buffer.test.js`, `tests/smart-reply.test.js`, `tests/support-focus.test.js`, `tests/theme.test.js`. Untracked: `dist/`, `tests/messages.test.js`, `tests/onboarding.test.js`, `tests/smart-reply-capture.test.js`.

Hashes SHA-256 de `SmartReplyService`, `GeminiService`, `cases.json` e relatório etapa 1 coincidem com a inspeção inicial. A propriedade Git da API foi contornada por `git -c safe.directory=...` por invocação, sem mudar configuração global. Nenhuma credencial ou prompt real foi salvo/versionado.

**Encerrado após preparação offline, aguardando autorização específica de custo e número máximo de chamadas para a amostra paga.**
