package com.soften.support.gemini_resumo.service;

import jakarta.annotation.PostConstruct;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
// import org.springframework.ai.document.Document;
import java.util.List;
import java.util.Map;

@Service
public class GeminiService {

    @Value("${gemini.api.key:}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final GoogleFileSearchService googleFileSearchService;

    public GeminiService(GoogleFileSearchService googleFileSearchService) {
        this.googleFileSearchService = googleFileSearchService;
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
     * Salva manualmente um resumo aprovado pelo atendente no Google File Search.
     */
    public void salvarResumoManual(String titulo, String conteudo) {
        try {
            // Extract "PROBLEMA / DÚVIDA" and "SOLUÇÃO APRESENTADA"
            StringBuilder textoSalvo = new StringBuilder();
            textoSalvo.append("TIPO: SOLUCAO_PASSADA\n");
            textoSalvo.append("TITULO: ").append(titulo).append("\n");

            String problema = extractSection(conteudo, "PROBLEMA / DÚVIDA:");
            String solucao = extractSection(conteudo, "SOLUÇÃO APRESENTADA:");

            if (problema != null)
                textoSalvo.append("PROBLEMA: ").append(problema).append("\n");
            if (solucao != null)
                textoSalvo.append("SOLUÇÃO: ").append(solucao).append("\n");

            if (problema == null && solucao == null) {
                // Fallback: save everything if parsing fails
                textoSalvo.append("CONTEUDO COMPLETO:\n").append(conteudo);
            }

            String fileName = "SOLUCAO_" + System.currentTimeMillis() + ".txt";
            googleFileSearchService.uploadFile(fileName,
                    textoSalvo.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8), "text/plain");

            System.out.println("✅ Solução salva no Google File Search: " + fileName);

        } catch (Exception e) {
            System.err.println("❌ Erro ao salvar solução manual: " + e.getMessage());
        }
    }

    private String extractSection(String text, String sectionName) {
        if (text == null || !text.contains(sectionName))
            return null;
        int start = text.indexOf(sectionName) + sectionName.length();
        // Better: Find next section or end of string.
        // Our format usually has headers like "SOLUÇÃO APRESENTADA:".
        // Let's take until double newline or next known header.

        // Simple line extraction for now, or block extraction
        String remainder = text.substring(start).trim();
        String[] nextHeaders = { "SOLUÇÃO APRESENTADA:", "OPORTUNIDADE DE UPSELL:", "PRINTS DE ERRO",
                "HUMOR DO CLIENTE:", "ATENDIMENTO:", "TÍTULO:" };

        int minIndex = remainder.length();
        for (String header : nextHeaders) {
            int idx = remainder.indexOf(header);
            if (idx != -1 && idx < minIndex) {
                minIndex = idx;
            }
        }
        return remainder.substring(0, minIndex).trim();
    }

    /**
     * Busca classificação (frase padrão) relevante baseada no resumo.
     */
    public List<Map<String, Object>> buscarDocumentacoes(String resumo) {
        List<Map<String, Object>> documentacoesSugeridas = new java.util.ArrayList<>();

        try {
            String searchContext = resumo;
            String problema = extractSection(resumo, "PROBLEMA / DÚVIDA:");
            if (problema != null)
                searchContext = problema;

            // Few-shot examples to teach the model the exact format
            String systemInstruction = "Você é um assistente de classificação que CITA LITERALMENTE linhas de um arquivo de documentação.\n"
                    +
                    "O arquivo CLASS_documentation_data_part1.txt contém categorias de problemas, uma por linha.\n" +
                    "Exemplos de linhas do arquivo:\n" +
                    "- ERRO: ACESSO NEGADO AO ACESSAR NF-E\n" +
                    "- DUVIDA: CADASTRAR PRODUTO/SERVIÇO\n" +
                    "- 508: CST INCOMPATÍVEL NA OPERAÇÃO COM NÃO CONTRIBUINTE\n" +
                    "- DUVIDA: CONFIGURAR BAIXA DE ESTOQUE PELA NFE / NFCE\n\n" +
                    "TAREFA: Encontre no arquivo a linha que melhor corresponde ao problema do usuário.\n" +
                    "REGRA CRÍTICA: Você DEVE copiar a linha EXATAMENTE como está no arquivo.\n" +
                    "NÃO invente, NÃO reformule, NÃO adicione prefixos como 'CLASS_'.\n" +
                    "Se não tiver certeza absoluta, retorne as 2-3 linhas mais próximas do arquivo.";

            // Direct query with emphasis on literal citation
            String query = "Problema do cliente: \"" + searchContext + "\"\n\n" +
                    "Busque no arquivo CLASS_documentation_data_part1.txt e retorne a linha LITERAL (cópia exata) que melhor classifica este problema.";

            String aiResponse = googleFileSearchService.simpleSearch(query, systemInstruction);

            org.springframework.ai.document.Document resultDoc = new org.springframework.ai.document.Document(
                    aiResponse,
                    Map.of("tipo", "sugestao_classificacao", "query", searchContext));

            documentacoesSugeridas.add(Map.of(
                    "id", resultDoc.getId(),
                    "content", resultDoc.getText(),
                    "metadata", resultDoc.getMetadata()));

        } catch (Exception e) {
            System.err.println("Erro na busca de classificação: " + e.getMessage());
        }

        return documentacoesSugeridas;
    }

    /**
     * Busca soluções em atendimentos passados similares usando Google File Search.
     */
    public List<String> buscarSolucoesSimilares(String problema) {
        List<String> solucoes = new java.util.ArrayList<>();
        try {
            System.out.println("🔍 [DEBUG] Buscando soluções similares para: " + problema);

            String prompt = "Verifique nos arquivos de SOLUÇÕES PASSADAS (TIPO: SOLUCAO_PASSADA) se existe algum caso similar a este: '"
                    + problema + "'. " +
                    "Se encontrar, descreva qual foi o problema e qual foi a solução aplicada. " +
                    "Se não encontrar nada similar, diga 'Nenhuma solução similar encontrada no histórico'.";

            String aiResponse = googleFileSearchService.simpleSearch(prompt);

            if (aiResponse != null && !aiResponse.contains("Nenhuma solução similar")) {
                solucoes.add(aiResponse);
            }

        } catch (Exception e) {
            System.err.println("❌ Erro ao buscar soluções similares: " + e.getMessage());
        }
        return solucoes;
    }

    /**
     * Busca documentação oficial (Legacy wrapper, unused mostly now but kept for
     * compatibility)
     */
    public List<org.springframework.ai.document.Document> buscarDocumentacaoOficialSmart(String query) {
        // Reusing logic via simpleSearch directly in other methods,
        // but keeping this if any controller calls it directly.
        List<org.springframework.ai.document.Document> docs = new java.util.ArrayList<>();
        try {
            String resp = googleFileSearchService.simpleSearch("Responda com base na documentação: " + query);
            docs.add(new org.springframework.ai.document.Document(resp));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return docs;
    }

    /**
     * Busca documentação oficial com filtro de categoria
     * 
     * @param query     Consulta de busca
     * @param categoria Categoria para filtrar (ex: "manuais", "nfe", "cte", etc.)
     * @return Lista de documentos filtrados pela categoria
     */
    public List<org.springframework.ai.document.Document> buscarDocumentacaoOficialSmart(String query,
            String categoria) {
        List<org.springframework.ai.document.Document> docs = new java.util.ArrayList<>();
        try {
            // Adiciona instrução de filtro por categoria na busca
            String filteredQuery = "Responda com base na documentação da categoria '" + categoria + "': " + query;
            String resp = googleFileSearchService.simpleSearch(filteredQuery);

            // Cria documento com metadata incluindo a categoria
            Map<String, Object> metadata = Map.of(
                    "tipo", "documentacao_oficial",
                    "categoria", categoria,
                    "query", query);
            docs.add(new org.springframework.ai.document.Document(resp, metadata));
        } catch (Exception e) {
            System.err.println("❌ Erro ao buscar documentação com filtro de categoria: " + e.getMessage());
            e.printStackTrace();
        }
        return docs;
    }
}
