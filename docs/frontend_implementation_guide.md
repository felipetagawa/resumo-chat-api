# Frontend Implementation Guide: Smart RAG Features

This guide details how to integrate the new "Smart Solution" and "Smart Docs" endpoints into the frontend application.

## 1. Feature: Smart Solution Suggestions

**Goal:** Allow agents to find technical solutions based on a problem description.

### UI Recommendation
*   Add a **"Buscar Solução"** section in the agent's sidebar or tool panel.
*   **Input:** A text area or input field labeled "Descreva o problema" (e.g., "Erro 503 no gateway").
*   **Action:** A button "Sugerir Solução".

### API Integration

**Endpoint:** `POST /api/gemini/solucoes`

```javascript
async function buscarSolucao(problemaDescricao) {
  try {
    const response = await fetch('https://[YOUR_API_URL]/api/gemini/solucoes', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        problema: problemaDescricao
      })
    });

    if (!response.ok) throw new Error('Erro na busca');

    const data = await response.json();
    // data.solucoesSugeridas is an Array of strings
    return data.solucoesSugeridas;
    
  } catch (error) {
    console.error('Falha ao buscar soluções:', error);
    return [];
  }
}
```

### Display Logic
*   If `solucoesSugeridas` is empty: Show message "Nenhuma solução similar encontrada."
*   If found: Display each solution in a card or list item.

---

## 2. Feature: Official Documentation Search

**Goal:** Allow agents to search the official knowledge base using natural language.

### UI Recommendation
*   Add a **"Base de Conhecimento"** search bar.
*   **Input:** Search input.

### API Integration

**Endpoint:** `GET /api/docs/search?query=...`

```javascript
async function buscarDocumentacao(termo) {
  try {
    const url = new URL('https://[YOUR_API_URL]/api/docs/search');
    url.searchParams.append('query', termo);

    const response = await fetch(url);
    if (!response.ok) throw new Error('Erro na busca');

    const data = await response.json();
    // data is an Array of Objects: [{ id, content, metadata }]
    return data;

  } catch (error) {
    console.error('Falha ao buscar docs:', error);
    return [];
  }
}
```

## 3. Best Practices (Workflow)
*   **Do NOT send raw chat logs** to these endpoints. The API is optimized for specific problem descriptions or queries.
*   **Pre-fill:** If a Summary has already been generated, you can pre-fill the "Solution Search" input with the generated Title.

---

## 4. Feature: Manual Knowledge Save (Critical)

**Goal:** Ensure only verified solutions are added to the knowledge base (Quality Control).
**Context:** The API no longer auto-saves summaries. You MUST call this endpoint explicitly.

### UI Recommendation
*   After the summary is generated (via `/resumir`), show a **Checkbox** or **Button**: `"Aprovar como Solução"`.
*   This input should only be clickable if the agent is satisfied with the summary.

### API Integration

**Endpoint:** `POST /api/gemini/salvar`

```javascript
async function aprovarESalvarResumo(titulo, conteudoResumo) {
  try {
    const response = await fetch('https://[YOUR_API_URL]/api/gemini/salvar', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        titulo: titulo,
        conteudo: conteudoResumo
      })
    });

    if (!response.ok) throw new Error('Erro ao salvar');

    const data = await response.json();
    alert('Resumo salvo na base de conhecimento!'); // Feedback pro usuário
    return true;

  } catch (error) {
    console.error('Falha ao salvar resumo:', error);
    alert('Erro ao salvar resumo. Tente novamente.');
    return false;
  }
}
```
