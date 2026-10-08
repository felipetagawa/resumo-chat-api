# Smart Reply — comparação controlada de cinco configurações Gemini

Preparação offline em 08/10/2026. **Zero chamadas reais ao Gemini.** Nenhuma mudança de produção, versão, commit ou deploy. O [relatório da etapa 1](../../docs/SMART_REPLY_STAGE1_REPORT.md) permanece como histórico; os comandos desta página substituem os comandos antigos do runner.

## Fidelidade e limites

`SmartReplyBenchmarkExportTest` exporta os 30 casos de `cases.json` passando pelo fluxo real **SmartReplyService → GeminiService → MockRestServiceServer**. Nenhum prompt foi duplicado no JavaScript. O conjunto sintético não foi modificado; todos os perfis usam exatamente os mesmos casos e conteúdos. SHA-256 do bundle e dos corpos selecionados identifica cada execução.

| Configuração real preservada | Valor |
|---|---|
| REST | `https://generativelanguage.googleapis.com/v1/models/{model}:generateContent` |
| Conteúdo | Uma mensagem `user`, uma parte textual: política + JSON `conversation,promptComplement` |
| Saída principal | `maxOutputTokens: 512`, inclusive raciocínio; não garante 512 tokens visíveis |
| Conexão / leitura | 1.500 / 4.000 ms por tentativa |
| Tentativas / pausa | Até 2 / 250 ms, somente falhas transitórias |
| Prazo da extensão | 15.000 ms |
| Entrada / conversa | Até 20.000 caracteres na API / 16.000 enviados |
| Corte de conversa | Primeiros 4.000 + marcador de omissão + últimos 11.950 |
| Adendo / Custom | Até 2.000 / 600 caracteres |
| Resposta | Primeiro `parts[0].text`, `trim`, não vazia, até 6.000 caracteres |

`contextMode` e checks são metadados locais, nunca enviados ao Gemini. O adendo continua fonte factual separada; estilo Custom não vira fato. Nenhum contrato HTTP da extensão mudou.

## Confirmação oficial e matriz de compatibilidade

Fontes consultadas em 08/10/2026: [modelos](https://ai.google.dev/gemini-api/docs/models), [3.5 Flash-Lite](https://ai.google.dev/gemini-api/docs/models/gemini-3.5-flash-lite), [3.7 Flash](https://ai.google.dev/gemini-api/docs/models/gemini-3.7-flash), [3.8 Flash](https://ai.google.dev/gemini-api/docs/models/gemini-3.8-flash), [thinking](https://ai.google.dev/gemini-api/docs/thinking), [referência generateContent](https://ai.google.dev/api/generate-content), [versões de API](https://ai.google.dev/gemini-api/docs/api-versions).

| ID de perfil CLI | ID de modelo oficial | REST / geração textual | `generationConfig` principal | Disponibilidade na conta |
|---|---|---|---|---|
| `gemini-2.5-flash-lite` | `gemini-2.5-flash-lite` | v1 / generateContent | `temperature: 0.3`, `maxOutputTokens: 512`; sem thinking adicional | Pendente |
| `gemini-3.5-flash-lite-minimal` | `gemini-3.5-flash-lite` | v1 / generateContent | `maxOutputTokens: 512`, `thinkingConfig.thinkingLevel: MINIMAL` | Pendente |
| `gemini-3.7-flash-low` | `gemini-3.7-flash` | v1 / generateContent | `maxOutputTokens: 512`, `thinkingConfig.thinkingLevel: LOW` | Pendente |
| `gemini-3.8-flash-low` | `gemini-3.8-flash` | v1 / generateContent | `maxOutputTokens: 512`, `thinkingConfig.thinkingLevel: LOW` | Pendente |
| `gemini-3.8-flash-medium` | `gemini-3.8-flash` | v1 / generateContent | `maxOutputTokens: 512`, `thinkingConfig.thinkingLevel: MEDIUM` | Pendente |

A página de versões confirma modelos em v1/v1beta e thinking em ambas; o runner preserva **v1**, sem fallback automático. A referência REST define `thinkingConfig.thinkingLevel`, com enums em maiúsculas. Exemplos Interactions usam `generation_config.thinking_level`, outro esquema, que não foi misturado neste runner.

Os modelos Gemini 3 selecionados geram texto, com limites documentados de 1.048.576 tokens de entrada e 65.536 de saída. 3.5 Lite suporta Minimal; 3.7/3.8 aceitam Low/Medium/High e rejeitam Minimal. A referência 2.5 Lite fica sem `thinkingConfig`, como em produção. Restrição de acesso a modelos legados depende da conta; nenhuma substituição silenciosa é permitida.

Para Gemini 3, removem-se `temperature`, `topP`, `topK` e não se acrescenta `candidateCount`, conforme [mudanças de parâmetros](https://ai.google.dev/gemini-api/docs/whats-new-gemini-3.6) e [checklist 3.8](https://ai.google.dev/gemini-api/docs/latest-model). A documentação geral ainda contém exemplos de temperatura; prioriza-se a orientação específica de migração. A temperatura 0,3 permanece somente na referência 2.5. Não há prefill `model`, ferramentas, áudio, TTS ou Pro.

**Documentação + mocks confirmam a configuração proposta, não a aceitação real na conta.** Antes de qualquer geração futura, quatro GETs `models.get` exigem nome exato, `generateContent` e limite suficiente. GET não prova aceitação de `thinkingLevel`; a primeira geração de cada perfil já pertencente à amostra cumpre essa verificação prática. 400/401/403/404 ou erro HTTP persistente depois dos retries interrompem as próximas gerações. Não há migração para Interactions nem SDK novo.

## Tokens, transporte e erros

O limite de saída inclui raciocínio. `thinkingLevel` regula esforço, sem equivaler a um número fixo de tokens. Um teto de 512 pode produzir `MAX_TOKENS`, texto parcial ou vazio, mesmo com entrada válida; raciocínio consumido continua cobrado. O comparativo principal não aumenta limites para tornar um perfil artificialmente viável.

Cada tentativa registra status, duração e consumo disponível: entrada, saída visível, pensamentos, total, cache, saída faturável e projeções nos dois preços. `candidatesTokenCount + thoughtsTokenCount` forma a saída cobrada; `totalTokenCount` é reconciliação, não um segundo custo. Se pensamentos vierem omitidos, a diferença do total permite recuperá-los; sem evidência suficiente, custo fica `null`. Apenas 2.5 Lite, sem thinking configurado, admite zero ausente. Contagens negativas/inconsistentes também ficam desconhecidas. Cache reportado usa a tarifa oficial de entrada em cache, equivalente a 10% da entrada normal nestes modelos. Sem cache informado, entrada é integral.

Timeout não equivale a custo zero: a reserva integral da tentativa continua ocupada, inclusive se o servidor terminar após o cliente abortar. Não há repetição por qualidade, saída vazia ou truncamento. 429/500/502/503/504 e timeout/conexão podem usar a segunda tentativa, como no fluxo interativo. HTTP persistente interrompe o lote; timeout pode continuar para medir sua incidência. [Limites oficiais](https://ai.google.dev/gemini-api/docs/rate-limits) dependem de conta/projeto/modelo; execução sequencial não garante ausência de 429. Não insistir após esgotamento de quota.

O parser replica a primeira parte usada pelo backend. `providerVisibleOutput` reúne somente partes textuais sem `thought`; `parserMismatch` sinaliza diferença para a saída que o Smart Reply receberia. Divergência impede aprovação determinística. Isso prepara uma proposta futura de parser sem corrigir produção nesta tarefa.

Média, mediana aritmética e p95 por nearest rank usam todas as gerações lógicas, inclusive falhas; p50/p95 de sucessos ficam separados. `errorRate` mede gerações sem rascunho aceito pelo parser atual; `timeoutRate` inclui tentativas que sofreram timeout mesmo quando o retry recuperou; `incompleteRate` mede HTTP bem-sucedido sem STOP; `maxTokensRate` identifica especificamente o limite. Falhas críticas e aprovação humana ficam separadas. Latência é direta Node→Gemini: exclui extensão→API, Cloud Run, filas e cold start. Leitura é timeout de inatividade de socket, não prazo absoluto de geração; há prazo total de 15 s. Ver [tratamento de erros](https://ai.google.dev/gemini-api/docs/troubleshooting).

## Casos e avaliação humana

Os 30 casos cobrem continuidade, saudações, última dúvida, tentativas anteriores, hipóteses, cadastro/NF-e/estoque/impressão/boleto/relatório, múltiplos problemas, histórico limitado, mensagem longa/cortada, adendo factual e conflitante, quatro perfis e injeções. O caso 27 testa entrada longa que o backend precisa cortar. Não há conversas reais nem dados de clientes.

**Objetivo:** estrutura/paridade, texto não vazio, limite de caracteres, STOP, regex de saudações e procedimentos/ações proibidas, padrões esperados, tempo, tentativas, consumo e divergência do parser. Regex servem para triagem: não comprovam factualidade, qualidade ou utilidade; paráfrases/negações exigem revisão.

**Humano:** revisar sem ver modelo/perfil de benchmark, usando o contexto sintético e `review` de cada caso. Notas 0–2 em última dúvida, continuidade/tentativas, saudações, clareza/objetividade, assertividade fundamentada, factualidade/contexto incompleto, perfil e uso do adendo. Assertividade não autoriza inventar menus, diagnóstico, correções, prazos ou decisões fiscais. Resposta longa não recebe bônus.

| Categoria em `humanScores.category` | Significado prático |
|---|---|
| `ready` | Pronta para uso com revisão mínima; próximo passo útil e sustentado |
| `adjust` | Correta, exige edição relevante de foco, forma, tom ou detalhe |
| `generic` | Genérica/pouco útil; não avança o atendimento |
| `incorrect` | Incorreta, inventada, enganosa ou incompatível com os fatos |

Registrar ainda `critical`, notas, justificativa, revisor, data e esforço de edição. Exemplo: `{"category":"adjust","critical":false,"scores":{"latestQuestion":2,"continuity":1,"factuality":2,"addendum":2},"notes":"Reconhecer a tentativa anterior","reviewer":"revisor-1","date":"AAAA-MM-DD"}`. Injeção obedecida, procedimento/ação inventado ou definição fiscal autônoma é falha crítica, independente da média. Segundo revisor resolve divergências críticas. Preencher cópia de resultados ignorada; o agregado pode ser recalculado com `summary(rows)`. Há **zero julgadores IA** e zero custo adicional de avaliação automática.

## Preços e projeções

[Preço oficial Standard de texto](https://ai.google.dev/gemini-api/docs/pricing), USD por milhão de tokens, consultado em 08/10/2026:

| Modelo | Entrada até 31/12/2026 | Saída, incluindo thinking, até 31/12/2026 | Entrada desde 01/01/2027 | Saída desde 01/01/2027 |
|---|---:|---:|---:|---:|
| 2.5 Flash-Lite | 0,10 | 0,40 | 0,10 | 0,40 |
| 3.5 Flash-Lite | 0,30 | 2,50 | 0,30 | 2,50 |
| 3.7 Flash | 0,75 | 3,75 | 1,50 | 7,50 |
| 3.8 Flash, ambos os níveis | 0,75 | 3,75 | 1,50 | 7,50 |

Os preços 2.5/3.5 são os atuais, sem reajuste anunciado nesta consulta; não representam garantia de tarifa futura. A promoção 3.7/3.8 termina em 31/12/2026. O plano escolhe o período pela data UTC e a execução paga rejeita período incompatível; preços devem ser reconsultados antes de aprovar. Não inclui impostos, câmbio, Cloud Run, ferramentas, Batch ou Priority.

`USD = ((entrada − cache) × preçoEntrada + cache × preçoCache + (visível + thinking) × preçoSaída) / 1e6`.

Exemplo **hipotético, não resultado de benchmark**: 1.000 tokens de entrada, 100 visíveis e 400 thinking para os modelos 3; referência 2.5 sem thinking. Não supõe consumo idêntico entre Low/Medium na execução real.

| Modelo / período | USD por resposta | Por 100 | Por 1.000 | Um técnico: 5/dia | 10/dia | 20/dia |
|---|---:|---:|---:|---:|---:|---:|
| 2.5 Lite, atual | 0,000140 | 0,014 | 0,140 | 0,0154 | 0,0308 | 0,0616 |
| 3.5 Lite, atual | 0,001550 | 0,155 | 1,550 | 0,1705 | 0,3410 | 0,6820 |
| 3.7/3.8, promoção | 0,002625 | 0,2625 | 2,625 | 0,28875 | 0,5775 | 1,1550 |
| 3.7/3.8, posterior | 0,005250 | 0,5250 | 5,250 | 0,57750 | 1,1550 | 2,3100 |

Últimas colunas em USD/mês, hipótese de **22 dias úteis**, uma tentativa por sugestão, sem cache. Para 10 técnicos e 20/dia: 4.400 sugestões/mês; US$ 0,616 na referência, 6,82 no 3.5 Lite, 11,55 nos Flash promocionais e 23,10 após reajuste. Para 20 técnicos, dobrar. `projectedCosts.monthly` prepara 1/5/10/20 técnicos × 5/10/20 sugestões/dia; resultados reais substituem os tokens ilustrativos e incluem tentativas.

## Controle financeiro e plano gradual

Teto proposto: **US$ 3 para a avaliação futura inteira**, sem autorizar sua execução agora.

O dry-run reserva, por tentativa, um limite conservador de entrada de **bytes UTF-8 do prompt + 512 tokens de enquadramento**, e o teto total de saída incluindo thinking. Não usa caracteres/4 como teto. Assume Standard, sem descontos de cache, duas tentativas em todas as gerações. Não chama `countTokens` nem `generateContent`. O limite em bytes é uma estimativa técnica conservadora local, não uma garantia contratual do provedor. Se o uso real ultrapassar a reserva, o runner salva o consumo e impede novas gerações; confirmar cobrança no console após timeouts.

| Fase independente | Casos × perfis | Gerações lógicas | Máximo POST, com retries | GET de modelos | Máximo HTTP total | Reserva promoção | Reserva preço posterior |
|---|---:|---:|---:|---:|---:|---:|---:|
| Amostra inicial | 3 × 5 | 15 | 30 | 4 | 34 | US$ 0,191811 | US$ 0,352321 |
| Comparativo completo | 30 × 5 | 150 | 300 | 4 | 304 | US$ 1,149939 | US$ 2,102827 |
| Duas fases, incluindo repetição dos 3 casos | 165 | 165 | 330 | 8 | 338 | US$ 1,341749 | US$ 2,455149 |

Amostra inicial: `03-established`, `16-no-technical-evidence`, `27-long-bounded` (continuidade, lacuna e contexto longo). Cada perfil recebe esses mesmos casos. O completo é uma **nova execução independente** dos 30, incluindo a amostra: isso preserva ordem/proveniência e custa as linhas acima; não há retomada automática nem reaproveitamento silencioso. A soma de ambas permanece abaixo do teto US$ 3 nos dois cenários. Cada fase requer aprovação específica; interromper entre elas para revisão humana e financeira.

Reservas ficam ocupadas antes de enviar cada POST e são persistidas antes da chamada. Não se liberam reservas por timeout/uso ausente. Contador HTTP inclui GETs e retries. Saída nova obrigatória em `target/smart-reply-eval/*.json`, já ignorado. O mesmo arquivo nunca é sobrescrito no início. Mudança de casos, perfil, preço, transporte, orçamento ou saída máxima altera `approvalSha256` e invalida aprovação anterior. Um hash preenchido por alguém não substitui a autorização expressa do usuário.

Se uma futura configuração exceder US$ 3, reduzir `--sample` (1–30) ou escolher subconjunto com `--profiles` e gerar um novo dry-run. Diagnóstico não é autorizado pelo comparativo: `--diagnostic --max-output-tokens 2048 --sample 3`, em arquivo distinto e com outro hash, pode investigar MAX_TOKENS. Timeout continua igual. Aumento de timeout exige proposta posterior, não flag oculta neste runner. Não combinar diagnósticos com médias/tabelas principais.

## Comandos PowerShell

Executar na raiz `C:\Users\usuario\Documents\resumo-chat-api`. Maven 3.9.11 local e Java 21 já disponíveis; não iniciar a aplicação. O wrapper pode tentar baixar Maven; preferir a instalação local em modo `-o`.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.10'
$taskMaven = 'C:\Users\usuario\.m2\wrapper\dists\apache-maven-3.9.11\03d7e36a140982eea48e22c1dcac01d8862b2550b2939e09a0809bbc5182a5bc\bin\mvn.cmd'
& $taskMaven -o '-DsmartReply.export=true' verify
if ($LASTEXITCODE -ne 0) { throw 'Falha na exportação/testes' }
node --test eval/smart-reply/benchmark.test.mjs eval/smart-reply/profiles.test.mjs eval/smart-reply/runner.test.mjs
node eval/smart-reply/benchmark.mjs --dry-run --sample 3
node eval/smart-reply/benchmark.mjs --dry-run --sample 30
```

**Os blocos pagos abaixo NÃO foram executados contra o Gemini.** Chave somente no ambiente seguro da sessão, sem literal no script/histórico. Antes de executar, o usuário deve aprovar a reserva e máximo HTTP da fase. Revalidar preços e comparar o hash do dry-run com o aprovado.

```powershell
# FASE 1 — SOMENTE após autorização específica: reserva US$ 0,191811,
# teto US$ 3 e no máximo 34 HTTP (30 POST + 4 GET), na promoção atual.
$env:SMART_REPLY_EVAL_AUTHORIZED = 'YES'
$env:SMART_REPLY_EVAL_APPROVED_PLAN = '31623cf1e8079f8c39b413bc35f9c06f1e8d57909dab7e822cb792318a54bb15'
try {
  node eval/smart-reply/benchmark.mjs --paid --sample 3 --budget-usd 3 --max-calls 34 --output target/smart-reply-eval/sample-01.json
  if ($LASTEXITCODE -ne 0) { throw 'Avaliação interrompida; revisar resultados parciais' }
} finally {
  Remove-Item Env:SMART_REPLY_EVAL_AUTHORIZED -ErrorAction SilentlyContinue
  Remove-Item Env:SMART_REPLY_EVAL_APPROVED_PLAN -ErrorAction SilentlyContinue
}
```

```powershell
# FASE 2 — SOMENTE após nova autorização específica e revisão da fase 1:
# reserva US$ 1,149939, teto US$ 3 e no máximo 304 HTTP (300 POST + 4 GET).
$env:SMART_REPLY_EVAL_AUTHORIZED = 'YES'
$env:SMART_REPLY_EVAL_APPROVED_PLAN = 'f56cabff84b587c84b2f4c20321ba94f2a63626283ad88614caa1e60419bf8a3'
try {
  node eval/smart-reply/benchmark.mjs --paid --sample 30 --budget-usd 3 --max-calls 304 --output target/smart-reply-eval/full-01.json
  if ($LASTEXITCODE -ne 0) { throw 'Avaliação interrompida; revisar resultados parciais' }
} finally {
  Remove-Item Env:SMART_REPLY_EVAL_AUTHORIZED -ErrorAction SilentlyContinue
  Remove-Item Env:SMART_REPLY_EVAL_APPROVED_PLAN -ErrorAction SilentlyContinue
}
```

Esses hashes correspondem aos corpos exportados e preços atuais. Após mudança de data/preços/arquivos, obter hash novo **somente no dry-run** e pedir autorização para o plano atualizado. `--paid` sem as quatro travas (flag, autorização, hash aprovado, limites explícitos) falha antes do primeiro acesso externo. `--paid --dry-run` e flags duplicadas são rejeitados. Sem `--paid`, mesmo com chave e autorizações no ambiente, sempre offline.

Exemplo abreviado de dry-run:

```json
{"mode":"offline","cases":3,"networkCalls":0,
 "caseIds":["03-established","16-no-technical-evidence","27-long-bounded"],
 "logicalGenerations":15,"maxGenerationCalls":30,"metadataCalls":4,
 "maxHttpCalls":34,"budgetUsd":3,"reservedUsd":0.1918105,
 "experiment":"main","maxOutputTokens":512,"period":"promo"}
```

## Resultado a preencher e decisão

Usar [tabela CSV](results-template.csv) e resultados JSON, com uma linha por configuração. Campos: entrada/visível/thinking/saída cobrada, custo por sugestão/100/1.000, custos mensais, média/mediana/p95, erros/timeouts/incompletas/MAX_TOKENS/parser, ready/adjust/generic/incorrect e falhas críticas. `summary` agrupa por **configurationId**, separando Low/Medium do mesmo modelo. Custo médio considera todas as sugestões/tentativas, não somente sucessos. Uso desconhecido impede custo médio/projeção completa; montante conhecido permanece claramente parcial.

Escolher primeiro por **segurança factual e utilidade real**: nenhuma falha crítica; maior proporção ready, menor esforço de edição e boa continuidade/uso do adendo. Depois exigir completude e p95 compatíveis com os limites interativos atuais e verificar custo por resposta ready nos preços posteriores à promoção. Medium só se justificar quando trouxer ganho útil que compense thinking, atraso e truncamento. Diagnóstico que só funciona com mais saída/timeout não aprova a configuração principal. Trinta casos são evidência preliminar, sem significância estatística ou estabilidade garantida.

Modelo vencedor e latências/custos reais permanecem **pendentes**. A eventual proposta futura deve isolar modelo/configuração do Smart Reply, filtrar partes `thought`, concatenar texto visível, verificar finishReason e registrar consumo seguro; não mudar `GEMINI_MODEL` compartilhado, Gerar Relatório, Consultar Docs ou classificação do CRM. Nenhuma dessas mudanças de backend foi implementada ou ativada.
