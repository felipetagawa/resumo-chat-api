# Tutorial: Atualização do Frontend (Extensão Chrome) - RAG com Google File Search

Este guia descreve as alterações necessárias no código da extensão do Chrome para se integrar com o novo backend "Stateless" (sem banco local) que utiliza o Google File Search.

## 1. Visão Geral das Mudanças

O backend agora processa as informações em três etapas distintas:
1.  **Resumir (`/api/gemini/resumir`)**: Gera o resumo do atendimento. (Mantido)
2.  **Classificar/Documentar (`/api/gemini/documentacoes`)**: Baseado no resumo, consulta os arquivos de texto (`documentation_data`) no Google File Search para retornar a **Frase Padrão** de classificação do problema.
3.  **Buscar Soluções Passadas (`/api/gemini/solucoes`)**: Busca em atendimentos anteriores já resolvidos. (Opcional por enquanto, se reativado)

## 2. Atualizar a Chamada de Classificação

No arquivo onde você processa a resposta do resumo (provavelmente `background.js` ou `content.js`), após receber o resumo com sucesso, você deve fazer uma segunda chamada para obter a classificação.

### Código Sugerido (Javascript):

```javascript
// Exemplo de função para buscar a classificação após ter o resumo em mãos
async function buscarClassificacao(resumoTexto) {
    try {
        const response = await fetch("https://SEU-BACKEND-URL/api/gemini/documentacoes", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({ resumo: resumoTexto })
        });

        if (!response.ok) throw new Error("Erro ao buscar documentação");

        const data = await response.json();
        
        // A resposta virá no formato:
        // { "documentacoesSugeridas": [ { "content": "402 - Rejeicao: XML...", "id": "...", "metadata": {...} } ] }
        
        return data.documentacoesSugeridas; // Retorna array de sugestões
    } catch (error) {
        console.error("Erro na classificação:", error);
        return [];
    }
}
```

## 3. Exibição no Popup / Interface

Quando a extensão exibir o resumo gerado, adicione uma seção chamada **"Classificação Sugerida"** ou **"Documentação Oficial"**.

*   Chame a função `buscarClassificacao(resumo)` passando o texto do resumo.
*   Mostre o resultado (`content`) que o backend devolveu.
*   **Nota:** O backend agora retorna a frase exata encontrada nos manuais (ex: "535: ERRO: IE DO DESTINATÁRIO NÃO VINCULADO NO CNPJ"). Isso ajuda o atendente a classificar o chamado corretamente no sistema.

## 4. (Opcional) Salvar Resumo Manual

Se você tiver um botão "Salvar" ou "Aprovar Resumo" no frontend, ele deve chamar o endpoint manual.
*   **Endpoint:** `/api/gemini/manual`
*   **Body:** `{ "titulo": "...", "conteudo": "..." }`
*   **Efeito:** Isso salvará o atendimento como um arquivo no Google File Search, tornando-o disponível para buscas de "Soluções Similares" no futuro.

---
**Resumo dos Endpoints Ativos:**
*   `POST /api/gemini/resumir` -> Gera o texto do resumo.
*   `POST /api/gemini/documentacoes` -> Retorna a classificação/frase padrão (usa arquivos `part1...5.txt`).
*   `GET /api/docs/search?query=...` -> Busca livre nos manuais (para tira-dúvidas).
