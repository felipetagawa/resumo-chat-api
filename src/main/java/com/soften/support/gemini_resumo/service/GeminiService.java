package com.soften.support.gemini_resumo.service;

import jakarta.annotation.PostConstruct;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class GeminiService {

    @Value("${gemini.api.key:}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String GEMINI_URL_BASE =
            "https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent?key=";

    @PostConstruct
    public void init() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Chave da Gemini não encontrada. Defina a variável de ambiente GEMINI_API_KEY " +
                            "ou configure gemini.api.key em application.properties"
            );
        }
    }

    /**
     * Gera um resumo a partir do texto do atendimento.
     *
     * @param textoAtendimento texto completo do atendimento
     * @return resumo gerado pela Gemini
     * @throws RuntimeException em caso de erro (HTTP, resposta inválida ou truncada)
     */
    public String gerarResumo(String textoAtendimento) {
        try {
            String prompt = "\n**Instrução Importante: Analise a conversa inteira, do início ao fim.** "
                    + "Ignore todas as mensagens do bot \"Automatico\". Foque apenas no cliente e no atendente humano.\n\n"
                    + "Analise o atendimento abaixo e resuma-o de forma concisa e direta, seguindo *exatamente* este formato:\n\n"
                    + "**PROBLEMA / DÚVIDA:** [Descreva em uma frase qual foi o problema ou dúvida principal do cliente, incluindo dados-chave como o número da nota, se houver.]\n"
                    + "**SOLUÇÃO APRESENTADA:** [Descreva os passos da solução de forma direta (ex: Atendente identificou o prazo, cancelou a nota, duplicou, corrigiu os dados e autorizou a nova).]\n"
                    + "**OPORTUNIDADE DE UPSELL:** [Responda apenas 'NÃO' ou 'SIM'.]\n"
                    + "**PRINTS DE ERRO OU DE MENSAGENS RELEVANTES:** [Responda apenas 'Não' ou 'Sim'.]\n"
                    + "**HUMOR DO CLIENTE:** [Descreva o humor em uma palavra (ex: Bom, Neutro, Irritado) e justifique brevemente (ex: \"Bom. Foi objetivo e agradeceu no final.\")]\n\n"
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
                String bodyText = response.getBody();
                String errMsg = "Erro HTTP: " + response.getStatusCodeValue();
                if (bodyText != null && !bodyText.isBlank()) {
                    errMsg += " - Corpo: " + bodyText;
                }
                throw new RuntimeException(errMsg);
            }

            String respBody = response.getBody();
            if (respBody == null || respBody.isBlank()) {
                throw new RuntimeException("Resposta vazia da API Gemini.");
            }

            JSONObject json = new JSONObject(respBody);

            String finishReason = null;
            try {
                finishReason = json
                        .getJSONArray("candidates")
                        .getJSONObject(0)
                        .optString("finishReason", null);
            } catch (Exception ignored) { }

            if ("MAX_TOKENS".equalsIgnoreCase(finishReason)) {
                throw new RuntimeException("Erro: A resposta da API foi cortada por exceder o limite de tokens. O resumo pode estar incompleto.");
            }

            String resumo;
            try {
                resumo = json
                        .getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text");
            } catch (Exception e) {
                throw new RuntimeException("Erro: não foi possível extrair o resumo da resposta da API. Resposta bruta: " + respBody);
            }

            if (resumo == null || resumo.isBlank()) {
                throw new RuntimeException("Erro: a API não retornou um resumo válido.");
            }

            return resumo;

        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException("Erro ao chamar a API Gemini: " + e.getMessage(), e);
        }
    }
}
