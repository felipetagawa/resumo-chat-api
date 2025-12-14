package com.soften.support.gemini_resumo.service;

import jakarta.annotation.PostConstruct;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.ai.document.Document;
import java.util.List;
import java.util.Map;

@Service
public class GeminiService {

    @Value("${gemini.api.key:}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final org.springframework.ai.vectorstore.VectorStore vectorStore;

    public GeminiService(org.springframework.ai.vectorstore.VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    private static final String GEMINI_URL_BASE = "https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash-lite:generateContent?key=";

    @PostConstruct
    public void init() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Chave da Gemini não encontrada. Defina a variável de ambiente GEMINI_API_KEY " +
                            "ou configure gemini.api.key em application.properties");
        }
    }

    /**
     * Gera um resumo a partir do texto do atendimento.
     *
     * @param textoAtendimento texto completo do atendimento
     * @return resumo gerado pela Gemini
     * @throws RuntimeException em caso de erro (HTTP, resposta inválida ou
     *                          truncada)
     */
    public com.soften.support.gemini_resumo.dto.ResumoResponse gerarResumo(String textoAtendimento) {
        String resumoTexto = null;
        String titulo = "Resumo do Atendimento"; // Default title

        try {

            String prompt = "\n**Instrução Importante: Analise a conversa inteira, do início ao fim.** "
                    + "Ignore todas as mensagens do bot \"Automatico\". Foque apenas no cliente e no atendente humano.\n"
                    + "Analise o atendimento abaixo e gere os seguintes itens:\n"
                    + "1. Um TÍTULO curto de uma frase resumindo o tema.\n"
                    + "2. O RESUMO detalhado no formato solicitado.\n\n"
                    + "Siga *exatamente* este formato de saída:\n"
                    + "TÍTULO: [Sua frase de título aqui]\n"
                    + "PROBLEMA / DÚVIDA: [Descreva em uma frase qual foi o problema ou dúvida principal...]\n"
                    + "SOLUÇÃO APRESENTADA: [Descreva os passos da solução...]\n"
                    + "OPORTUNIDADE DE UPSELL: [Responda apenas 'NÃO' ou 'SIM'.]\n"
                    + "PRINTS DE ERRO OU DE MENSAGENS RELEVANTES: [Responda apenas 'Não' ou 'Sim'.]\n"
                    + "HUMOR DO CLIENTE: [Descreva o humor em uma palavra e justifique...]\n\n"
                    + "ATENDIMENTO:\n"
                    + textoAtendimento + "\n";

            JSONObject body = new JSONObject();

            JSONArray contents = new JSONArray();
            JSONObject contentItem = new JSONObject();
            contentItem.put("role", "user");
            JSONArray parts = new JSONArray();
            parts.put(new JSONObject().put("text", prompt));
            contentItem.put("parts", parts);
            contents.put(contentItem);
            body.put("contents", contents);

            JSONObject generationConfig = new JSONObject();
            generationConfig.put("temperature", 0.3);
            generationConfig.put("maxOutputTokens", 2048);
            body.put("generationConfig", generationConfig);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);

            String url = GEMINI_URL_BASE + apiKey;
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Erro HTTP: " + response.getStatusCode().value());
            }

            String respBody = response.getBody();
            if (respBody == null || respBody.isBlank()) {
                throw new RuntimeException("Resposta vazia da API Gemini.");
            }

            JSONObject json = new JSONObject(respBody);
            String rawText = json.getJSONArray("candidates")
                    .getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                    .getJSONObject(0).getString("text");

            // Normalize rawText to extract Summary and Title
            if (rawText.contains("TÍTULO:")) {
                int tituloStart = rawText.indexOf("TÍTULO:") + 7;
                int tituloEnd = rawText.indexOf("\n", tituloStart);
                if (tituloEnd > tituloStart) {
                    titulo = rawText.substring(tituloStart, tituloEnd).trim();
                    resumoTexto = rawText.substring(tituloEnd).trim();
                } else {
                    resumoTexto = rawText;
                }
            } else {
                resumoTexto = rawText;
            }

            // AUTO-SAVE REMOVED per user request (Manual Learning)
            // The generated summary is now just returned, waiting for human approval.

            return new com.soften.support.gemini_resumo.dto.ResumoResponse(titulo, resumoTexto);

        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Erro ao chamar a API Gemini: " + e.getMessage(), e);
        }
    }

    /**
     * Salva manualmente um resumo aprovado pelo atendente.
     */
    public void salvarResumoManual(String titulo, String conteudo) {
        if (conteudo == null || conteudo.isBlank())
            return;
        try {
            Document doc = new Document(conteudo,
                    Map.of("tipo", "resumo_automatico", "origem", "manual_approval", "titulo",
                            titulo != null ? titulo : "Sem Título"));
            vectorStore.add(List.of(doc));
            System.out.println("Resumo salvo MANUALMENTE no VectorStore.");
        } catch (Exception e) {
            throw new RuntimeException("Erro ao salvar resumo: " + e.getMessage());
        }
    }

    /**
     * Busca documentações oficiais relevantes baseadas no resumo gerado.
     * 
     * @param resumo resumo do atendimento previamente gerado
     * @return lista de documentações sugeridas (Map com id, content, metadata)
     */
    public List<Map<String, Object>> buscarDocumentacoes(String resumo) {
        List<Map<String, Object>> documentacoesSugeridas = new java.util.ArrayList<>();

        try {
            String searchContext = resumo;

            // Attempt to extract "PROBLEMA / DÚVIDA" section for better context
            if (resumo != null && resumo.contains("PROBLEMA / DÚVIDA:")) {
                int problemaStart = resumo.indexOf("PROBLEMA / DÚVIDA:") + 18;
                int problemaEnd = resumo.indexOf("\n", problemaStart);
                if (problemaEnd > problemaStart) {
                    searchContext = resumo.substring(problemaStart, problemaEnd).trim();
                } else if (resumo.length() > problemaStart) {
                    searchContext = resumo.substring(problemaStart).trim();
                }
            }

            // Use Smart RAG
            List<org.springframework.ai.document.Document> officialDocs = buscarDocumentacaoOficialSmart(searchContext);

            for (org.springframework.ai.document.Document doc : officialDocs) {
                documentacoesSugeridas.add(Map.of(
                        "id", doc.getId(),
                        "content", doc.getText(),
                        "metadata", doc.getMetadata()));
            }
        } catch (Exception e) {
            System.err.println("Erro na busca de documentação oficial: " + e.getMessage());
        }

        return documentacoesSugeridas;
    }

    /**
     * Busca soluções em atendimentos passados similares.
     *
     * @param problema descrição do problema atual
     * @return lista de soluções encontradas em casos similares
     */
    public List<String> buscarSolucoesSimilares(String problema) {
        List<String> solucoes = new java.util.ArrayList<>();

        try {
            // 1. Recuperação (Retrieval)
            List<org.springframework.ai.document.Document> docs = vectorStore.similaritySearch(
                    org.springframework.ai.vectorstore.SearchRequest.builder()
                            .query(problema)
                            .filterExpression("tipo == 'resumo_automatico'")
                            .topK(3) // Pegamos os 3 mais próximos
                            .build());

            if (docs.isEmpty()) {
                return solucoes;
            }

            // 2. Montagem do Contexto
            StringBuilder contextBuilder = new StringBuilder();
            for (org.springframework.ai.document.Document doc : docs) {
                contextBuilder.append("---\n").append(doc.getText()).append("\n");
            }

            // 3. Geração Aumentada (Generative Step)
            String prompt = "Você é um especialista em suporte técnico. \n" +
                    "O usuário tem o seguinte problema: \"" + problema + "\"\n\n" +
                    "Abaixo estão casos passados que podem ou não ser relevantes:\n" +
                    contextBuilder.toString() + "\n" +
                    "Analise os casos passados. Se houver uma solução que se aplique ao problema ATUAL, " +
                    "extraia e adapte a solução de forma clara e direta. " +
                    "Se os casos passados não tiverem relação com o problema atual, responda APENAS: 'Nenhuma solução relevante encontrada.'\n"
                    +
                    "Não invente informações. Use apenas o contexto fornecido.";

            // Reutilizando a lógica de chamada via RestTemplate (simplificado para exemplo,
            // idealmente refatorar num metodo privado auxiliar)
            JSONObject body = new JSONObject();
            JSONArray contents = new JSONArray();
            JSONObject contentItem = new JSONObject();
            contentItem.put("role", "user");
            JSONArray parts = new JSONArray();
            parts.put(new JSONObject().put("text", prompt));
            contentItem.put("parts", parts);
            contents.put(contentItem);
            body.put("contents", contents);

            // Configurar tokens menores pois a resposta deve ser concisa
            JSONObject generationConfig = new JSONObject();
            generationConfig.put("temperature", 0.1); // Temperatura baixa para ser fiel ao contexto
            generationConfig.put("maxOutputTokens", 500);
            body.put("generationConfig", generationConfig);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);

            String url = GEMINI_URL_BASE + apiKey;
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JSONObject json = new JSONObject(response.getBody());
                String aiResopnse = json.getJSONArray("candidates")
                        .getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                        .getJSONObject(0).getString("text").trim();

                if (!aiResopnse.contains("Nenhuma solução relevante encontrada")) {
                    solucoes.add(aiResopnse);
                }
            }

        } catch (Exception e) {
            System.err.println("Erro na busca inteligente de soluções: " + e.getMessage());
            // Fallback silencioso ou log
        }

        return solucoes;
    }

    /**
     * Busca documentação oficial filtrando por relevância via Gemini (Smart RAG).
     *
     * @param query termo de busca do usuário
     * @return lista de documentos validados como relevantes
     */
    public List<org.springframework.ai.document.Document> buscarDocumentacaoOficialSmart(String query) {
        List<org.springframework.ai.document.Document> relevantDocs = new java.util.ArrayList<>();

        try {
            System.out.println("🔍 [DEBUG] Buscando documentação para query: " + query);

            // 1. Retrieval (Busca Vetorial Ampla - Top 5)
            List<org.springframework.ai.document.Document> candidates = vectorStore.similaritySearch(
                    org.springframework.ai.vectorstore.SearchRequest.builder()
                            .query(query)
                            .filterExpression("tipo == 'documentacao_oficial'")
                            .topK(5)
                            .build());

            System.out.println("📊 [DEBUG] Candidatos encontrados: " + candidates.size());

            if (candidates.isEmpty()) {
                System.out.println("⚠️ [DEBUG] Nenhum candidato encontrado no VectorStore!");
                return relevantDocs;
            }

            // Log dos candidatos encontrados
            for (int i = 0; i < candidates.size(); i++) {
                var doc = candidates.get(i);
                System.out.println("📄 [DEBUG] Candidato " + i + ": " +
                        doc.getMetadata().get("titulo") + " | Content: " +
                        doc.getText().substring(0, Math.min(100, doc.getText().length())) + "...");
            }

            // 2. Prepare Context for Validation
            JSONObject jsonContext = new JSONObject();
            JSONArray docsArray = new JSONArray();
            for (int i = 0; i < candidates.size(); i++) {
                JSONObject docJson = new JSONObject();
                docJson.put("id", String.valueOf(i));
                docJson.put("content", candidates.get(i).getText());
                docsArray.put(docJson);
            }
            jsonContext.put("documents", docsArray);
            jsonContext.put("user_query", query);

            // 3. Generative Validation Prompt
            String prompt = "Você é um filtro de relevância documental. " +
                    "Analise a query do usuário e os documentos candidatos. " +
                    "Retorne APENAS um objeto JSON válido sem nenhum texto adicional.\n\n" +
                    "JSON de Entrada: " + jsonContext.toString() + "\n\n" +
                    "Tarefa: Retorne um JSON contendo uma lista de IDs dos documentos que são REALMENTE relevantes para a query. "
                    +
                    "Se nenhum for relevante, retorne uma lista vazia.\n\n" +
                    "IMPORTANTE: Sua resposta deve ser APENAS o JSON, sem ```json ou qualquer outro texto.\n\n" +
                    "Formato de Saída: {\"relevant_ids\": [\"0\", \"2\"]}";

            // Call Gemini
            JSONObject body = new JSONObject();
            JSONArray contents = new JSONArray();
            JSONObject contentItem = new JSONObject();
            contentItem.put("role", "user");
            contentItem.put("parts", new JSONArray().put(new JSONObject().put("text", prompt)));
            contents.put(contentItem);
            body.put("contents", contents);

            JSONObject generationConfig = new JSONObject();
            generationConfig.put("temperature", 0.0); // Zero criatividade, pura lógica
            generationConfig.put("maxOutputTokens", 100); // Resposta curta
            body.put("generationConfig", generationConfig);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);

            String url = GEMINI_URL_BASE + apiKey;
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String responseText = new JSONObject(response.getBody())
                        .getJSONArray("candidates").getJSONObject(0)
                        .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
                        .getString("text");

                System.out.println("🤖 [DEBUG] Resposta do Gemini: " + responseText);

                // Clean response - remove markdown code blocks if present
                String cleanedResponse = responseText.trim();
                if (cleanedResponse.startsWith("```json")) {
                    cleanedResponse = cleanedResponse.substring(7);
                }
                if (cleanedResponse.startsWith("```")) {
                    cleanedResponse = cleanedResponse.substring(3);
                }
                if (cleanedResponse.endsWith("```")) {
                    cleanedResponse = cleanedResponse.substring(0, cleanedResponse.length() - 3);
                }
                cleanedResponse = cleanedResponse.trim();

                System.out.println("🧹 [DEBUG] Resposta limpa: " + cleanedResponse);

                // Parse result
                JSONObject resultJson = new JSONObject(cleanedResponse);
                JSONArray validIds = resultJson.optJSONArray("relevant_ids");

                System.out.println("✅ [DEBUG] IDs relevantes retornados: " +
                        (validIds != null ? validIds.toString() : "null"));

                if (validIds != null) {
                    for (int i = 0; i < validIds.length(); i++) {
                        int index = Integer.parseInt(validIds.getString(i));
                        if (index >= 0 && index < candidates.size()) {
                            relevantDocs.add(candidates.get(index));
                        }
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("❌ [DEBUG] Erro no Smart Docs RAG: " + e.getMessage());
            e.printStackTrace();
            // Fallback: se der erro na validação, retorna o top 1 da busca burra (melhor
            // que nada)
            // Ou retorna vazio se preferir conservadorismo. Aqui vamos retornar vazio para
            // evitar alucinação.
        }

        System.out.println("📚 [DEBUG] Total de docs relevantes: " + relevantDocs.size());
        return relevantDocs;
    }
}
