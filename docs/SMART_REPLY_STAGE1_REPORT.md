# AtendeAI Manager — primeira etapa do Smart Reply

08/10/2026. Implementação local concluída, aguardando QA. Nenhuma avaliação real, commit, push, merge, deploy, alteração de versão ou publicação.

## Estado inicial e escopo

Os dois checkouts locais estavam na branch `main`. A API estava limpa. A extensão tinha alterações locais em `content.js`, módulos de captura/mensagens/Smart Reply/tema/UI, estilos e testes, além de `dist/` e testes novos não versionados. Esses arquivos foram preservados. O Git da API foi consultado com `-c safe.directory=...`, somente por comando, sem mudar a configuração global. Nenhum arquivo de produção da extensão ou do CRM foi alterado.

## Achado e alteração

O prompt existente exigia factualidade, segurança e concisão, mas não explicitava que a sugestão é a próxima fala de uma conversa em andamento. Também orientava o perfil Empathetic a reconhecer frustração sem condicionar essa emoção à evidência disponível. São lacunas nas instruções; a causalidade e frequência das respostas ruins ainda não foram medidas no Gemini.

`SmartReplyService.java` passou a orientar continuidade, prioridade à última mensagem, uso pertinente do histórico, proibição de cumprimentos/apresentações repetidos, reconhecimento de tentativas e resultados, distinção de hipótese/fato/lacuna e ausência de procedimentos/ações inventados. Primeiro contato explícito permite saudação breve. Contextos parciais/omitidos não permitem supor histórico. Empathetic acolhe sem presumir frustração.

Adendo continua separado da conversa e não pode mudar regras. Custom continua somente como estilo subordinado às regras factuais. Revisão humana, proteção contra injeção e restrições fiscais/promessas foram preservadas.

**Não foram alterados** `GeminiService`, DTOs, controllers, configuração global, limites, retries, versões ou dependências. O fluxo normal permanece uma geração lógica, com o tratamento transitório existente. Gerar Relatório/outros recursos mantêm o mesmo modelo compartilhado.

## Entregáveis

- `SmartReplyContinuityTest`: integração do serviço com o corpo HTTP real e transporte mockado; quatro perfis, restrições de continuidade/factualidade, separação de dados, configuração e modelo padrão.
- `SmartReplyPrivacyTest`: sete campos de notas privadas/resumo são rejeitados no contrato HTTP antes do provedor.
- `SmartReplyBenchmarkExportTest`: 30 casos sintéticos atravessam o código real e são exportados somente com `-DsmartReply.export=true`; nenhuma conexão externa.
- `eval/smart-reply/cases.json`: casos identificados, perfil/contexto, checks determinísticos e expectativa de revisão humana.
- `eval/smart-reply/benchmark.mjs`: runner isolado, sem dependências npm/produção, offline por padrão, geração paga com duas travas explícitas e preflight de disponibilidade em `v1`.
- `eval/smart-reply/benchmark.test.mjs`: testes locais do runner/checks/custos/retries e modo seguro.
- [Manual de avaliação e comandos](../eval/smart-reply/README.md): limites, modelos/fontes, custo/latência, rubrica humana, recomendações e execução futura.

## Evidência local

| Verificação | Resultado |
|---|---|
| Baseline focado antes da mudança | 30 testes passaram |
| Novo teste de continuidade antes do ajuste | 4 falhas esperadas, uma por perfil |
| Focados depois do ajuste + exportação | 35 testes passaram |
| Suíte completa API, incluindo privacidade | 92 testes; 0 falhas/erros/skips |
| `mvn -o -DsmartReply.export=true verify` | Compilação, testes e empacotamento passaram |
| Testes Node do avaliador | 11 passaram |
| CLI offline | 30 casos; plano de 60 gerações; nenhuma conexão |
| Sintaxe JavaScript / `git diff --check` | Passaram |
| Suíte existente da extensão (`node --test`, todos os arquivos de testes) | 289 passaram, sem mudanças da tarefa |

Usou-se Java 21.0.10, Maven 3.9.11 local e Node 25.8.0. Maven funcionou offline com cache existente. Logs de verificação da API estão em `target/smart-reply-focused.log` e `target/smart-reply-verify.log`; exportação em `target/smart-reply-eval/requests.json`. Todos ignorados pelo Git. Há avisos preexistentes de APIs depreciadas do Spring e instrumentação dinâmica Mockito/Byte Buddy; não impediram o build. O Git avisa conversão LF/CRLF, sem erro de whitespace.

## Critérios de aceitação e limites da validação

| Critério | Evidência / pendência |
|---|---|
| Conversas em andamento | Instruções verificadas no HTTP enviado; aderência do Gemini exige teste real |
| Saudações/fatos inventados | Java verifica proibições; Node detecta exemplos sintéticos ruins; não prova saída real |
| Perfis existentes | Quatro perfis testados e casos de benchmark; qualidade subjetiva depende de revisão |
| Contrato HTTP | DTO/controller intactos; testes existentes e privacidade passaram |
| Modelo global | Configuração intacta; testes capturam URL `gemini-2.5-flash-lite` |
| Benchmark reproduzível | Corpos exportados pelo código real, SHA-256, IDs fixos, checks e rubrica documentados |
| Custo/latência | Métricas por tentativa, raciocínio incluído no custo, custo desconhecido separado, p50/p95; medições reais pendentes |
| Sem chamada paga | Somente mocks e CLI offline; nenhuma chave carregada para geração |
| Extensão/CRM fora do escopo | Estado local preservado; suíte da extensão passou |

## Riscos e recomendação

Prompts reduzem risco, mas não garantem factualidade nem resistência absoluta à injeção. Regex são triagem e precisam de interpretação humana. O corpo maior de instruções aumenta tokens de entrada; o impacto precisa de medição real. O orçamento de 512 tokens pode resultar em truncamento, especialmente com raciocínio; não foi aumentado. O backend já aceita primeiro texto de resposta `MAX_TOKENS`; o benchmark registra isso como falha de qualidade e não paga nova geração.

O avaliador mede Gemini diretamente: não inclui trânsito extensão→API, filas, Cloud Run ou inicialização. Disponibilidade dos modelos na conta e compatibilidade efetiva com `v1` não foram testadas. A documentação oficial foi consultada; não houve autenticação de conta ou envio de cenários ao provedor. A alternativa documentada `gemini-3.5-flash-lite` fica fora da comparação padrão e exige inclusão autorizada + verificação real. Sem troca automática nem fallback para outro endpoint/modelo.

Manter `gemini-2.5-flash-lite` global. Flash custa 3× por token de entrada e 6,25× por saída conforme preços standard consultados; ainda não há ganho de qualidade/latência medido que justifique mudar. Em etapa futura, considerar modelo específico para Smart Reply escolhido manualmente, independente de Gerar Relatório, somente se melhorar respostas aprovadas por humanos nos limites atuais e justificar custo incremental. Nenhuma configuração desse tipo foi implementada.

Revisão humana usa sete dimensões de 0–2, com falhas críticas separadas. Não há julgador LLM: custo de avaliação por LLM é zero. Os comandos de execução paga estão no manual e **não foram executados**. Aguardar QA e autorização separada para qualquer comparação real ou operação de produção.
