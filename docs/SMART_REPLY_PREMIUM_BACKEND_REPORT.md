**Smart Reply: seleção isolada de Gemini 3.8 Flash Low — entrega para QA**

08/10/2026. Repositório local: `felipetagawa/resumo-chat-api`, branch `main`, HEAD `4b19537`. Implementação local concluída e validada offline. **Premium desabilitado por padrão. A ativação em produção ainda não pode ser recomendada.** Faltam confirmação do modelo efetivo, consumo/fatura atuais e avaliação real controlada de qualidade, latência, erros e tokens.

Nenhuma chamada paga ao Gemini, commit, push, merge, deploy, publicação, mudança de extensão, banco ou infraestrutura foi realizada. Esta entrega aguarda QA e aprovação; este documento não autoriza nenhuma etapa paga nem alteração de produção.

A inspeção inicial encontrou alterações anteriores em `SmartReplyService.java`, relatórios das etapas 1/2, benchmark e testes de continuidade, exportação e privacidade. A extensão também já tinha alterações locais. Foram capturados hashes SHA-256 de 168 arquivos dos dois repositórios. A comparação final encontrou somente os cinco arquivos preexistentes modificados por esta etapa, sem arquivos ausentes. `SmartReplyService.java`, todos os arquivos anteriores de `eval/`, os relatórios anteriores, os testes anteriores e todos os arquivos da extensão conservaram seus hashes. Nenhuma alteração foi staged.

A consulta somente leitura ao serviço `gemini-resumo`, projeto `core-synthesis-478512-t6`, região `southamerica-east1`, não foi concluída. O sandbox inicialmente impediu acesso ao diretório do gcloud; após a revisão automática permitir o acesso local necessário, a renovação da autenticação falhou por timeout em `oauth2.googleapis.com`. Não foram exibidas credenciais ou variáveis sensíveis. Portanto, **o modelo efetivo em produção permanece não confirmado**. O fallback local em Java/YAML é `gemini-2.5-flash-lite`; isso não comprova o ambiente da revisão que recebe tráfego. Antes da ativação, consultar exclusivamente GEMINI_MODEL, eventual referência a segredo, revisão e tráfego do serviço e das revisões ativas; se a variável estiver ausente, conferir o fallback do artefato efetivamente implantado.

Arquivos desta etapa:

| Arquivo relativo à API | Estado | Alteração |
|---|---|---|
| `src/main/java/com/soften/support/gemini_resumo/config/GeminiApiProperties.java` | Modificado | Seleção separada, chave de habilitação, validação e timeout limitado |
| `src/main/java/com/soften/support/gemini_resumo/config/RestTemplateConfig.java` | Modificado | Timeout exclusivo do Smart Reply, mantendo sobrecarga compatível |
| `src/main/java/com/soften/support/gemini_resumo/service/GeminiService.java` | Modificado | Modelo por chamada, Low, parser interativo e metadados por tentativa |
| `src/main/java/com/soften/support/gemini_resumo/service/GoogleFileSearchService.java` | Modificado | Telemetria de Docs/classificação, mantendo modelo, parâmetros e respostas |
| `src/main/resources/application.yml` | Modificado | Três configurações novas; linha de GEMINI_MODEL preservada |
| `src/main/java/com/soften/support/gemini_resumo/service/GeminiUsage.java` | Novo | Registros agregados e estimativa de custo por período |
| `src/test/java/com/soften/support/gemini_resumo/service/GeminiSmartReplyModelTest.java` | Novo | Isolamento, habilitação, partes, bloqueios, limites e privacidade |
| `src/test/java/com/soften/support/gemini_resumo/service/GeminiUsageTest.java` | Novo | Raciocínio, cache, virada de ano e consumo desconhecido |
| `src/test/java/com/soften/support/gemini_resumo/service/GeminiFileSearchUsageTest.java` | Novo | Docs mantém modelo global e texto com premium configurado |
| `eval/smart-reply/backend-eval.mjs` | Novo | Adaptador do benchmark para o novo parser |
| `eval/smart-reply/backend-eval.test.mjs` | Novo | Partes visíveis e rejeição de respostas incompletas |
| `eval/smart-reply/monthly-cost.mjs` | Novo | Calculadora offline de cenários mensais e registros agregados |
| `eval/smart-reply/monthly-cost.test.mjs` | Novo | Limites financeiros, retries e evidência incompleta |
| `docs/SMART_REPLY_PREMIUM_BACKEND_REPORT.md` | Novo | Este relatório e procedimento de operação |

A arquitetura existente é Spring Boot 3.5.7 / Java 21, REST generateContent. `GeminiApiProperties` fornece chave, modelo global, base v1, retries e timeouts. `GeminiService` atende Relatório, ask e geração interativa. `SmartReplyService` continua responsável pelo prompt, perfis, adendos, continuidade e validação do rascunho, sem alterações nesta etapa. Docs/classificação passam por `GoogleFileSearchService`, que usa v1beta e o modelo global. Nenhum DTO, endpoint, contrato da extensão ou comportamento funcional de Relatório/Docs foi alterado.

| Configuração | Padrão | Comportamento |
|---|---|---|
| `GEMINI_MODEL` | Fallback preexistente 2.5 Lite | Inalterado; continua atendendo as demais funções |
| `GEMINI_SMART_REPLY_MODEL` | Vazio | Herda o modelo global |
| `GEMINI_SMART_REPLY_PREMIUM_ENABLED` | `false` | Candidato 3.8 configurado retorna ao global enquanto desabilitado |
| `GEMINI_SMART_REPLY_READ_TIMEOUT_MILLIS` | `4000` | Limitado entre 1000 e 5000 ms; conexão permanece 1500 ms |

O backend aceita herdar/selecionar o modelo global e, fora dele, somente os candidatos 2.5 Lite ou 3.8 Flash. Configuração desconhecida falha antes da chamada e também é validada na inicialização. A extensão não escolhe o modelo. Habilitar a chave com modelo vazio continua herdando o global: são necessárias a seleção do candidato e a habilitação para sair do modelo global.

Configuração preparada, ainda desabilitada:

```text
GEMINI_SMART_REPLY_MODEL=gemini-3.8-flash
GEMINI_SMART_REPLY_PREMIUM_ENABLED=false
GEMINI_SMART_REPLY_READ_TIMEOUT_MILLIS=4000
```

O candidato envia `thinkingConfig.thinkingLevel: LOW`, sem temperatura, topP, topK ou candidateCount. A referência econômica mantém temperatura 0,3 e nenhum thinking adicional. O Smart Reply conserva **512 tokens de saída e no máximo duas tentativas**, com backoff limitado a 250 ms. Não há retry para melhorar qualidade, aumentar tokens ou contornar respostas bloqueadas/incompletas. O parser une todas as partes textuais visíveis do primeiro candidato, exclui `thought: true`, rejeita conteúdo vazio e finishReason explícito diferente de STOP. A ausência de finishReason mantém compatibilidade com respostas antigas; o avaliador exige STOP para considerar uma geração completa. Os parsers e parâmetros das demais funcionalidades permanecem como antes.

A documentação específica do [Gemini 3.8 e checklist de migração](https://ai.google.dev/gemini-api/docs/latest-model) fundamenta a remoção da amostragem e a configuração Low. O schema REST continua generateContent, sem migração para Interactions/SDK novo. [Versões da API](https://ai.google.dev/gemini-api/docs/api-versions) e [referência generateContent](https://ai.google.dev/api/generate-content) foram consultadas. A disponibilidade efetiva de v1/modelo/parâmetros na conta ainda depende do preflight e das primeiras gerações autorizadas.

O logger `gemini.usage` emite JSON por tentativa: modelo efetivamente solicitado, funcionalidade, tentativa, tokens de entrada, saída e raciocínio, total/cache, duração, success/timeout/error, completude do consumo, preços de 2026/2027 e custo estimado vigente por data UTC. Funcionalidades: smart_reply, report, ask, docs, classification e file_search. Em File Search, success indica sucesso do transporte; em Smart Reply, Relatório e ask, o parser correspondente é validado antes desse registro. A duração é por tentativa; latência total e p95 por sugestão devem vir do benchmark e das medições do endpoint.

**Esses registros não contêm conversa, adendo, prompt, resposta, pensamento textual, identidade, chave, URI ou corpo de erro.** A verificação de privacidade captura o logger real em testes. Não foi criada persistência adicional nem um contador global de orçamento. A auditoria não pretende substituir uma revisão geral dos logs legados de todos os endpoints.

Saída faturável = saída visível + raciocínio, sem dupla contagem. Metadados ausentes, inválidos ou inconsistentes e modelo sem preço conhecido produzem custo nulo/desconhecido. Pensamentos ausentes só são inferidos quando o total permite reconciliar os contadores. Timeouts sem resposta não significam custo zero. O cálculo contempla tokens de texto Standard e desconto do cache informado; armazenamento de cache, embeddings/importação de File Search, ferramentas, Cloud Run/logs, outros provedores, impostos, câmbio e créditos não estão incluídos nesse evento. Esses gastos entram separadamente na projeção total e precisam ser reconciliados com faturamento.

Preços Standard em USD por milhão de tokens, verificados em 08/10/2026 na [tabela oficial](https://ai.google.dev/gemini-api/docs/pricing):

| Modelo/período | Entrada | Saída, incluindo raciocínio | Entrada em cache |
|---|---:|---:|---:|
| 2.5 Flash Lite, referência | 0,10 | 0,40 | 0,01 |
| 3.8 Flash, até 31/12/2026 | 0,75 | 3,75 | 0,075 |
| 3.8 Flash, desde 01/01/2027 | 1,50 | 7,50 | 0,15 |

Os preços de 2027 são os anunciados hoje, sujeitos a nova verificação. Não se presume gratuidade por free tier, créditos ou promoção além da data publicada.

Não há série real de tokens, volume mensal, retries ou faturas disponível nesta investigação. A calculadora produz um **exemplo hipotético**, não uma previsão observada: 1000 tokens de entrada, 100 de saída visível, zero raciocínio na referência, 200 no premium, uma tentativa, sem cache e US$ 1/mês de outros gastos. Volumes equivalem a 22 dias úteis. Todos os Smart Replies usam premium nesta tabela, mas as demais funções mantêm seu custo.

| Smart Replies/mês | Atual hipotético | Seletivo 2026 | Razão 2026 | Seletivo 2027 | Razão 2027 |
|---:|---:|---:|---:|---:|---:|
| 220 | US$ 1,0308 | US$ 1,4125 | 1,37× | US$ 1,8250 | 1,77× |
| 1100 | US$ 1,1540 | US$ 3,0625 | 2,65× | US$ 5,1250 | 4,44× |
| 2200 | US$ 1,3080 | US$ 5,1250 | 3,92× | US$ 9,2500 | 7,07× |

No exemplo de 1100 sugestões, estar abaixo de US$ 4 em 2026 não cumpre o teto de 2×. A parcela matemática máxima de premium seria aproximadamente 60,5% em 2026 e 29,1% em 2027 para o teto de 2×; isso não é recomendação de rollout e exige margem.

A fórmula usada é `C_atual = O + N*b` e `C_seletivo = O + N*((1-f)*b + f*p)`, onde O inclui os demais gastos, N é o volume mensal, f é a fração premium e b/p são custos por solicitação incluindo retries. Exigir `C_seletivo <= 2*C_atual` e buscar `C_seletivo < 4`, nos dois períodos.

Para reproduzir apenas o exemplo:

```powershell
node eval/smart-reply/monthly-cost.mjs
```

Posteriormente, fornecer somente os JSON agregados do logger, uma linha por evento, de todas as instâncias/revisões e de uma janela completa; não fornecer logs com prompts ou respostas:

```powershell
node eval/smart-reply/monthly-cost.mjs --usage target/smart-reply-eval/usage.jsonl --days 7 --premium-share 0.05 --extra-monthly-usd 1
```

O valor 1 é um exemplo: substituir pelo gasto adicional real não coberto pelos tokens. A projeção observada usa 30 dias corridos, soma custos de todas as tentativas por solicitação e não transforma custos desconhecidos em zero. Precisam existir amostras representativas dos dois modelos; amostras do benchmark sintético não representam volume mensal de produção. A ferramenta nunca recomenda ativação automaticamente, mesmo com projeção numérica favorável.

| Verificação final | Resultado |
|---|---|
| Maven 3.9.11 / Java 21.0.10, `-o -DsmartReply.export=true verify` | **103 testes, zero falhas/erros/skips; BUILD SUCCESS** |
| Testes unitários e integração Spring/MockRestServiceServer | Incluídos no verify |
| Continuidade, perfis, adendos, saudações e ausência de invenções | Testes anteriores preservados e executados; avaliação semântica real continua pendente |
| Compilação e empacotamento Spring Boot | Passaram |
| Node benchmark/perfis/runner + dois módulos novos | **31 testes, zero falhas/skips** |
| `node --check` de todos os .mjs do avaliador | Passou |
| Exportação Java real de prompts sintéticos | 30 casos, transporte inteiramente interceptado |
| Dry-run pareado, amostra 3 e completo 30 | Offline; zero chamadas de rede |
| `git diff --check` em ambos os repositórios | Passou |
| Hashes de arquivos preexistentes e index Git | Trabalho anterior preservado; nada staged |

Os quatro primeiros testes de modelo/parser falharam antes da implementação pelo comportamento antigo e passaram depois. A primeira compilação intermediária identificou a assinatura antiga de RestTemplate usada por um teste; a sobrecarga compatível foi restaurada. A primeira execução Node no sandbox falhou em três testes de runner ao escrever os artefatos locais; executados com o acesso local autorizado, todos passaram sem alterar o benchmark. Avisos preexistentes de Mockito/instrumentação e normalização LF/CRLF não impediram os checks. A suíte da extensão não foi executada nesta etapa, pois nenhum arquivo dela foi alterado; sua preservação foi verificada por hashes.

Artefatos ignorados em `target/smart-reply-eval/`: requests.json, backend-dry-run-2026.json, backend-dry-run-2027.json, backend-full-dry-run.json e monthly-hypothetical.json. Logs finais do build: `C:/Users/usuario/AppData/Local/Temp/atendeai-verify-final.log`. Hash de requests exportados mantido: `e6c52dc937a4544c555b15d30dd6feeb45e7dee4463b48438c2b722b76dfdee6`.

O benchmark histórico permanece intacto e ainda diagnostica o parser de primeira parte. Usar o novo `backend-eval.mjs` para avaliar o parser implementado; ele reaproveita transporte, preços, casos, resumo, limites e todas as guardas de aprovação do runner. Preserva a diferença do parser antigo como diagnóstico e avalia partes visíveis/STOP. O benchmark usa exclusivamente casos sintéticos; os rascunhos guardados para revisão humana pertencem ao resultado sintético, separado dos registros de consumo de produção.

A pequena validação real proposta compara referência econômica confirmada com 3.8 Low nos casos 03-established, 16-no-technical-evidence e 27-long-bounded: **seis gerações lógicas, no máximo 12 POST e dois GET de metadados**. 512 tokens, duas tentativas, leitura de 4 s. Reserva conservadora offline de **US$ 0,0603301 em 2026** ou **US$ 0,1138336 em 2027**, com teto proposto US$ 0,20. A reserva usa bytes UTF-8 com margem para entrada, não tokens medidos; não garante a cobrança do provedor.

Dry-run seguro, já executado:

```powershell
node eval/smart-reply/backend-eval.mjs --dry-run --sample 3 --profiles gemini-2.5-flash-lite,gemini-3.8-flash-low --budget-usd 0.20
```

Para a fase paga futura, obter autorização separada para esse plano. Só então usar as guardas documentadas no [README anterior](../eval/smart-reply/README.md): `--paid`, `SMART_REPLY_EVAL_AUTHORIZED=YES`, `SMART_REPLY_EVAL_APPROVED_PLAN` com o hash exato do dry-run atualizado, `--max-calls 14`, `--budget-usd 0.20`, mesmos perfis/amostra e arquivo de saída novo dentro do target. Chave somente no processo, nunca em arquivo ou console. Confirmar modelo efetivo e acesso da conta antes da geração; uma referência diferente exige revisar perfis/plano/preços, sem substituir silenciosamente o global. O modo paid não foi executado nesta etapa.

Revisar os resultados antes de autorizar um lote maior. Comparar ready/adjust/generic/incorrect, falhas críticas, custo por rascunho ready, entrada/saída/pensamentos, latência mediana/p95 total, timeouts/erros e MAX_TOKENS. Zero invenções, continuidade correta, respeito ao perfil/adendo e ausência de saudações repetidas são critérios necessários. Falha de completude ou timeout não justifica aumentar tokens/retries automaticamente. O plano completo pareado dos 30 casos tem 60 gerações lógicas, no máximo 122 HTTP e reserva 2026 de US$ 0,3569081; exigiria aprovação própria.

| Comparação real | Modelo atual | 3.8 Low |
|---|---|---|
| Qualidade/utilidade, falhas críticas | Não medida nesta etapa | Não medida |
| Latência e taxa de erros/timeouts | Sem série real consultada | Não medida |
| Entrada, saída e raciocínio | Sem consumo real disponível | Não medido |
| Custo por solicitação e mensal total | Não confirmado por fatura | Apenas cenários e reservas |

512 tokens incluem o esforço de raciocínio; Low não equivale a um teto fixo de pensamentos. O prazo de 4 s pode inviabilizar o ganho de qualidade. Limitar a configuração de leitura a 5 s evita expansão ilimitada; não constitui uma garantia rigorosa do prazo total de 15 s da extensão, que inclui fila, rede, cold start e outros custos de execução. Não há fallback pago automático do premium para outro modelo.

O [limite experimental de gastos do AI Studio](https://ai.google.dev/gemini-api/docs/billing) admite atraso de processamento e ultrapassagem; não garante bloqueio no valor exato. Rate limits ou contadores em memória por instância do Cloud Run se multiplicam com escala, concorrência, reinicializações e múltiplas revisões: **não são orçamento global confiável**. Os limites de chamadas/reservas do benchmark só controlam aquele processo experimental. Confirmar fatura, agregar métricas existentes de todas as instâncias e manter margem abaixo dos dois objetivos.

Procedimento futuro, condicionado a QA e aprovações:

1. Aprovar este código local; confirmar o modelo, revisão/tráfego, acesso e faturamento atuais. Conservar o premium desligado.
2. Autorizar separadamente a amostra paga sintética de seis gerações e revisar os resultados. Obter dados de consumo real e gastos adicionais; isso pode exigir uma implantação somente de telemetria, também com aprovação própria, mantendo o premium desligado.
3. Se a referência e os objetivos financeiros forem sustentados por dados, aprovar uma revisão do **mesmo serviço** com GEMINI_MODEL preservado, modelo específico 3.8, habilitação true e leitura inicialmente 4000 ms. Manter a revisão econômica disponível; não criar serviço novo.
4. Aprovar divisão de tráfego entre revisões, por exemplo 5% premium / 95% econômica, depois 10% e 25% somente após revisão de qualidade, latência, erros e projeção total de 2026/2027 com margem. O percentual é tráfego do serviço existente, não seleção enviada pela extensão. Relatório/Docs continuam usando GEMINI_MODEL em ambas.
5. Interromper a expansão ou fazer rollback por invenção/falha crítica, degradação de latência/completude, custos desconhecidos não reconciliados ou projeção acima de 2×/US$ 4. Não avançar automaticamente a 100%.

Rollback preferencial: devolver 100% do tráfego à revisão econômica validada e retirar o tráfego de revisões premium. Desabilitar `GEMINI_SMART_REPLY_PREMIUM_ENABLED` em uma revisão do mesmo serviço também restaura o global; limpar o modelo específico restaura herança. Alterar variável de ambiente produz nova revisão, portanto não é uma operação puramente local/instantânea. A mudança de tráfego não cancela chamadas já em andamento, e revisões premium devem ser consideradas ao verificar o consumo residual. **Não alterar GEMINI_MODEL** nem a extensão. Nenhum desses passos foi executado.

A decisão permanece: implementação disponível para QA, candidato premium preparado e desabilitado; **não recomendar ativação em produção com os dados atuais**. A aprovação depende do modelo efetivo e da fatura baseline, resultados reais pareados e projeção do custo mensal total em ambos os períodos.
