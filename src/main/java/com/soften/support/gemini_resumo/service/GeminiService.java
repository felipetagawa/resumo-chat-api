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
        List<String> recomendacoesDocs = new java.util.ArrayList<>();
        List<String> documentacoesSugeridas = new java.util.ArrayList<>();

        try {
            // 1. RAG - Busca 1: Recomendações (Histórico de Dicas)
            try {
                List<org.springframework.ai.document.Document> historyDocs = vectorStore.similaritySearch(
                        org.springframework.ai.vectorstore.SearchRequest.builder()
                                .query(textoAtendimento)
                                .filterExpression("tipo == 'resumo_automatico'")
                                .topK(3)
                                .build());

                for (org.springframework.ai.document.Document doc : historyDocs) {
                    recomendacoesDocs.add(extractProblemAndSolution(doc.getText()));
                }
            } catch (Exception e) {
                System.err.println("Erro na busca de histórico: " + e.getMessage());
            }

            // 2. RAG - Busca 2: Documentação Oficial (Preenchimento Direto)
            // Alterado para retornar diretamente as 3 mais similares do banco, garantindo
            // que não venha vazio.
            try {
                List<org.springframework.ai.document.Document> officialDocs = vectorStore.similaritySearch(
                        org.springframework.ai.vectorstore.SearchRequest.builder()
                                .query(textoAtendimento)
                                .filterExpression("tipo == 'documentacao_oficial'")
                                .topK(3) // Retorna as 3 mais relevantes
                                .build());

                for (org.springframework.ai.document.Document doc : officialDocs) {
                    if (doc.getText() != null && !doc.getText().isBlank()) {
                        documentacoesSugeridas.add(doc.getText());
                    }
                }
            } catch (Exception e) {
                System.err.println("Erro na busca de documentação oficial: " + e.getMessage());
            }

            StringBuilder contextoDocs = new StringBuilder();

            // Add History Tips to Context
            if (!recomendacoesDocs.isEmpty()) {
                contextoDocs.append("\n\n**DICAS DE CASOS ANTERIORES (Histórico):**\n");
                for (String doc : recomendacoesDocs) {
                    contextoDocs.append("- ").append(doc).append("\n");
                }
            }

            // Add Official Documentation to Input Context (Optional, helps Gemini
            // understand context)
            if (!documentacoesSugeridas.isEmpty()) {
                contextoDocs.append("\n\n**CONTEXTO TÉCNICO (Documentação Relacionada):**\n");
                for (String doc : documentacoesSugeridas) {
                    contextoDocs.append("- ").append(doc).append("\n");
                }
            }

            String prompt = "\n**Instrução Importante: Analise a conversa inteira, do início ao fim.** "
                    + "Ignore todas as mensagens do bot \"Automatico\". Foque apenas no cliente e no atendente humano.\n"
                    + contextoDocs.toString() + "\n"
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
                throw new RuntimeException("Erro HTTP: " + response.getStatusCodeValue());
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

            // Persistir o resumo gerado como conhecimento futuro (Auto-learning)
            if (resumoTexto != null && !resumoTexto.isBlank()) {
                try {
                    Document doc = new Document(resumoTexto,
                            Map.of("tipo", "resumo_automatico", "origem", "gemini", "titulo", titulo));
                    vectorStore.add(List.of(doc));
                    System.out.println("Resumo salvo no VectorStore com sucesso.");
                } catch (Exception e) {
                    System.err.println("Erro ao salvar resumo no VectorStore: " + e.getMessage());
                }
            }

            return new com.soften.support.gemini_resumo.dto.ResumoResponse(titulo, resumoTexto, recomendacoesDocs,
                    documentacoesSugeridas);

        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Erro ao chamar a API Gemini: " + e.getMessage(), e);
        }
    }

    private String extractProblemAndSolution(String fullText) {
        if (fullText == null || fullText.isEmpty()) {
            return "";
        }

        // 1. Formato Gemini (Rico)
        String geminiSolucaoKey = "**SOLUÇÃO APRESENTADA:**";
        String geminiProblemaKey = "**PROBLEMA / DÚVIDA:**";
        String geminiUpsellKey = "**OPORTUNIDADE DE UPSELL:**";

        if (fullText.contains(geminiSolucaoKey) && fullText.contains(geminiProblemaKey)) {
            try {
                int probStart = fullText.indexOf(geminiProblemaKey) + geminiProblemaKey.length();
                int probEnd = fullText.indexOf(geminiSolucaoKey);

                int solStart = fullText.indexOf(geminiSolucaoKey) + geminiSolucaoKey.length();
                int solEnd = fullText.indexOf(geminiUpsellKey);

                String problema = fullText.substring(probStart, probEnd).trim();
                String solucao;
                if (solEnd != -1 && solEnd > solStart) {
                    solucao = fullText.substring(solStart, solEnd).trim();
                } else {
                    solucao = fullText.substring(solStart).trim();
                }

                // Prioridade para a Solução (Dica)
                return "💡 **SUGESTÃO:** " + solucao + "\n(Contexto: " + problema + ")";
            } catch (Exception e) {
                return fullText; // Fallback se falhar o parse
            }
        }

        // 2. Formato Legado / Simples (Ex: "Erro: X. Solução: Y.")
        // Tenta achar "Solução:" ou "Solucao:"
        String legacyKey = "Solução:";
        int legacyIndex = fullText.indexOf(legacyKey);
        if (legacyIndex == -1) {
            legacyKey = "Solucao:";
            legacyIndex = fullText.indexOf(legacyKey);
        }

        if (legacyIndex != -1) {
            try {
                String problema = fullText.substring(0, legacyIndex).replace("Erro:", "").trim();
                String solucao = fullText.substring(legacyIndex + legacyKey.length()).trim();
                return "💡 **SUGESTÃO:** " + solucao + "\n(Contexto: " + problema + ")";
            } catch (Exception e) {
                return fullText;
            }
        }

        // 3. Fallback (Texto original)
        return fullText;
    }
}
