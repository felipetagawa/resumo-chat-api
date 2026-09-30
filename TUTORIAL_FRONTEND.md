# Tutorial: integração da extensão Chrome com a API stateless

A extensão pode gerar um resumo, gerar dicas para o atendimento atual e consultar os manuais no Google File Search. O backend não consulta chamados anteriores nem persiste resumos.

## Resumo do atendimento

Envie a conversa atual para `POST /api/gemini/resumir`:

```javascript
const response = await fetch(`${apiBase}/api/gemini/resumir`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ texto: conversaAtual, promptComplement: observacoesOpcionais })
});
if (!response.ok) throw new Error((await response.json()).erro);
const { summary } = await response.json();
```

`promptComplement` é opcional e limitado a 2.000 caracteres. Para clientes que enviam texto puro, o endpoint também aceita `Content-Type: text/plain` e retorna o mesmo campo `summary`.

## Dicas Inteligentes

Envie o atendimento atual para `POST /api/chamado/processar-dica` com `{ "texto": "...", "promptComplement": "..." }`. A resposta inclui `summary` (objeto com `fullSummary`, `problem`, `solution` e `module`), `tips` (lista de próximos passos) e `status`. Este endpoint gera o resumo novamente; não recebe um resumo já pronto. Mostre as ações registradas em `summary.solution` separadamente das sugestões em `tips`.

```javascript
const response = await fetch(`${apiBase}/api/chamado/processar-dica`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ texto: conversaAtual })
});
if (!response.ok) throw new Error((await response.json()).erro);
const { summary, tips } = await response.json();
```

Os campos `SimilarTagsFound` e `solutionsAnalyzed` são `0`: não há busca de chamados históricos.

## Documentação oficial

Para uma busca livre nos manuais, use `GET /api/docs/search?query=...` com um problema ou termo específico. O parâmetro opcional `categoria` tem valor padrão `manuais`. A resposta é uma lista de objetos `{ id, content, metadata }`. Essa busca permanece integrada ao Google File Search.

```javascript
const url = new URL(`${apiBase}/api/docs/search`);
url.searchParams.set('query', termo);
const response = await fetch(url);
if (!response.ok) throw new Error('Falha ao consultar os manuais');
const documentos = await response.json();
```

## Salvamento e compatibilidade

Não há salvamento de resumos no fluxo atual. `POST /api/chamado/salvar-resumo` responde `410 Gone`; remova o botão de aprovação/salvamento ou trate essa resposta em versões antigas da extensão. `POST /api/pre-controls` também responde `410 Gone`; `GET /api/pre-controls` devolve uma página vazia compatível.

## Endpoints ativos para a extensão

| Endpoint | Uso |
| --- | --- |
| `GET /api/gemini/ping` | Verificar disponibilidade da aplicação |
| `POST /api/gemini/resumir` | Gerar resumo da conversa atual |
| `POST /api/chamado/processar-dica` | Gerar resumo e dicas da conversa atual |
| `GET /api/docs/search?query=...` | Buscar documentação oficial |
