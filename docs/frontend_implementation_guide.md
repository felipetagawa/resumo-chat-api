# Frontend integration guide (stateless API)

The API processes each request from the current conversation. It does not search stored calls or save summaries.

## Generate a summary

`POST /api/gemini/resumir` accepts JSON with `texto` and optional `promptComplement` (up to 2,000 characters). The legacy `complemento` field is also accepted. It also accepts a plain-text body with `Content-Type: text/plain`. Both return `{ "summary": "..." }`.

```javascript
const response = await fetch(`${apiBase}/api/gemini/resumir`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ texto: conversaAtual, promptComplement: notasDoAtendente })
});
if (!response.ok) throw new Error((await response.json()).erro);
const { summary } = await response.json();
```

## Generate smart tips

`POST /api/chamado/processar-dica` accepts the same JSON fields. It generates a new summary and suggestions from the current conversation. The response includes `summary`, `problemDetected`, `moduleDetected`, `tips` (an array of strings), `status`, `SimilarTagsFound: 0`, and `solutionsAnalyzed: 0`. The zero counts reflect that stored calls are no longer searched.

```javascript
const response = await fetch(`${apiBase}/api/chamado/processar-dica`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ texto: conversaAtual, promptComplement: notasDoAtendente })
});
if (!response.ok) throw new Error((await response.json()).erro);
const { summary, tips } = await response.json();
```

Render `tips` as next steps and keep `summary.solution` as the record of actions already performed.

## Search official documentation

`GET /api/docs/search?query=...` searches the existing Google File Search manual store. Optional `categoria` defaults to `manuais`. The response is an array of `{ id, content, metadata }`.

```javascript
const url = new URL(`${apiBase}/api/docs/search`);
url.searchParams.set('query', problemaOuTermo);
const response = await fetch(url);
if (!response.ok) throw new Error('Falha na busca de documentação');
const documents = await response.json();
```

Pass a focused problem or search term to the documentation search. Use the full current conversation for summary and tips.

## Persistence

There is no endpoint to approve or save a summary to the knowledge base. `POST /api/chamado/salvar-resumo` returns `410 Gone`. Remove save actions from the frontend flow or handle that response explicitly in older clients.
