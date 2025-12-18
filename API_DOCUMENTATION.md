# Documentação da API - Resumo Chat

Visão geral dos endpoints disponíveis na API do Resumo Chat.

## Autenticação
A maioria dos endpoints requer a chave da API do Gemini configurada no backend (`GEMINI_API_KEY`). Não há autenticação de usuário implementada nestes endpoints públicos por enquanto.

## Endpoints

### 1. Resumo (`GeminiController`)
Responsável por gerar resumos de atendimentos e buscar soluções.

**Base URL:** `/api/gemini`

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `POST` | `/resumir` | Gera um resumo estruturado de um atendimento. Aceita JSON ou Plain Text. |
| `POST` | `/solucoes` | Busca soluções similares na base de conhecimento para um problema específico. |
| `POST` | `/salvar` | Salva manualmente um resumo ou solução na base de conhecimento (Google File Search). |
| `GET`  | `/ping` | Health check. Retorna status "ok". |

#### Exemplos de Payload

**`/resumir` (JSON)**
```json
{
  "texto": "Conteúdo completo do chat do atendimento..."
}
```

**`/solucoes`**
```json
{
  "problema": "Erro 202 falha de conexão ao emitir NF-e"
}
```

**`/salvar`**
```json
{
  "titulo": "Erro de Conexão NF-e",
  "conteudo": "Solução aplicada: Reiniciar serviço de spooler..."
}
```

---

### 2. Documentação & RAG (`DocumentationController`)
Gerencia o upload de manuais, frases de classificação e busca inteligente (RAG - Retrieval Augmented Generation).

**Base URL:** `/api/docs`

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `GET`  | `/search` | Busca inteligente de documentação baseada em query. Retorna trechos relevantes. |
| `POST` | `/` | Upload de manuais (PDF, TXT, MD) para a base de conhecimento "Manuais". |
| `POST` | `/classification` | Upload de arquivos de frases de classificação para o store "Classification". |
| `GET`  | `/list` | Lista todos os arquivos presentes nos stores do Google File Search. |
| `GET`  | `/store-info` | Exibe metadados e estatísticas dos stores (quantidade de arquivos, status, etc.). |
| `POST` | `/reset` | **CUIDADO**: Apaga TODOS os stores e arquivos do Google File Search conectados à API. |
| `DELETE`| `/{id}` | Deleta um arquivo específico pelo seu ID (ex: `files/abc-123`). |

#### Parâmetros de Busca (`/search`)
- `query` (obrigatório): O termo ou frase a ser pesquisada.
- `categoria` (opcional, default="manuais"): Filtra o contexto da busca (ex: "manuais", "erro", "instalação").

**Exemplo:**
`GET /api/docs/search?query=como emitir nfe&categoria=fiscal`

#### Upload de Arquivos (`POST /` e `POST /classification`)
Requer `multipart/form-data`.
- `file`: O arquivo a ser enviado.
- `categoria`: (Opcional) Metadado de categoria.
- `modulo`: (Opcional) Metadado de módulo.
- `tags`: (Opcional) Tags separadas por vírgula.
- `descricao`: (Opcional) Descrição do arquivo.

---

## Estrutura de Resposta Padrão
Em caso de erro, a API retorna:
```json
{
  "erro": "Descrição do erro ocorrido"
}
```
Status Codes comuns:
- `200 OK`: Sucesso.
- `400 Bad Request`: Parâmetros inválidos ou faltando.
- `500 Internal Server Error`: Erro no processamento (ex: falha na API do Gemini).
