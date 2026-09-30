# API Resumo Chat

A API usa `GEMINI_API_KEY` para geração de texto e Google File Search. Não requer banco de dados.

## Resumo e relatório

`POST /api/gemini/resumir` aceita JSON com `texto` obrigatório e `promptComplement` opcional. O alias `complemento` também é aceito. Aceita ainda `text/plain` com o atendimento completo. Retorna `{"summary":"..."}`. O resumo não é salvo no servidor. Falhas da Gemini retornam `{"erro":"..."}` com status adequado, sem detalhes internos.

`GET /api/gemini/ping` retorna `{"status":"ok","app":"gemini-summary"}`.

## Dicas Inteligentes

`POST /api/chamado/processar-dica` aceita `{"texto":"...","promptComplement":"..."}`. A API gera uma análise e dicas com Gemini usando apenas o atendimento atual. O retorno mantém `summary`, `problemDetected`, `moduleDetected`, `SimilarTagsFound`, `solutionsAnalyzed`, `tips` e `status`. Os dois contadores históricos são sempre `0`, porque não há histórico no backend.

`POST /api/chamado/salvar-resumo` retorna `410 Gone`: a persistência de resumos foi desativada.

## Documentação

Os endpoints `/api/docs` continuam usando Google File Search: `GET /search?query=...&categoria=...`, `POST /` (multipart com `file`), `GET /list`, `GET /store-info`, `DELETE /{id}` e `POST /reset`. `reset` apaga os stores do Google File Search; use com cuidado.

## PreControl

`POST /api/pre-controls` retorna `410 Gone` com `PRE profile persistence is disabled`. `GET /api/pre-controls` mantém um retorno paginado vazio (`200 OK`), pois o backend não guarda registros de PreControl.

## Classificação de Produto (Jev)

`POST /api/classification/product` recebe `{"conversation":"..."}` e classifica o atendimento entre os 55 Produtos oficiais do CRM usando Jev/TypeSafe. A chave fica somente no backend em `TYPESAFE_API_KEY`.

A resposta contém `mode` (`single`, `multiple` ou `uncertain`), até três `suggestions` com `productId`, `product` e `probability`, além de `confidence`, `unclearProbability` e `latencyMs`. A API não persiste a conversa nem a classificação.
