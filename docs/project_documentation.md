# Arquitetura da Resumo Chat API

O backend Spring Boot 3.5 / Java 21 é stateless. O fluxo de resumo recebe o atendimento em `/api/gemini/resumir`, chama Gemini e devolve `summary`; o histórico local da extensão fica sob responsabilidade do frontend. Nenhum resumo é salvo pela API.

Dicas Inteligentes em `/api/chamado/processar-dica` produzem um resumo estruturado e, em seguida, enviam o atendimento e a análise atual à Gemini para gerar dicas. Os campos de contagem de chamados similares e soluções históricas permanecem no JSON com valor `0` por compatibilidade.

A busca e gestão de documentação em `/api/docs` usam Google File Search diretamente. Elas não dependem de PostgreSQL. `GoogleFileSearchService` inicializa os stores em segundo plano quando há uma chave Gemini configurada.

PreControl não tem mais persistência: criação responde `410 Gone` e listagem retorna página vazia. `/api/chamado/salvar-resumo` também responde `410 Gone`.

Configuração necessária para a integração: `GEMINI_API_KEY`. As opções `GEMINI_MODEL`, `GEMINI_GENERATE_CONTENT_BASE_URL` e parâmetros de retry/timeout são opcionais e têm defaults em `application.yml`. Não há datasource, JPA, Hibernate, PostgreSQL ou Flyway no runtime.
