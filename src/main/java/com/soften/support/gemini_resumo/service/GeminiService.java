package com.soften.support.gemini_resumo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class GeminiService {

    @Value("${gemini.api.key:}")
    private String apiKey;
    private final RestTemplate restTemplate = new RestTemplate();
    private static final String GEMINI_URL_BASE = "https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash-lite:generateContent?key=";

    public GeminiService() {
    }

    @PostConstruct
    public void init() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Chave da Gemini não encontrada. Defina a variável de ambiente GEMINI_API_KEY " +
                            "ou configure gemini.api.key em application.properties"
            );
        }
    }

    private String generateGenericSummary(String textService, String contextPrompt) {
        try {
            String prompt = contextPrompt + "\n\nATENDIMENTO ANALISADO:\n" + textService + "\n";

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

            String finishReason = json
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .optString("finishReason", null);

            if ("MAX_TOKENS".equalsIgnoreCase(finishReason)) {
                throw new RuntimeException("Erro: A resposta da API foi cortada por exceder o limite de tokens.");
            }

            String summary = json
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text");

            if (summary == null || summary.isBlank()) {
                throw new RuntimeException("Erro: a API não retornou um summary válido.");
            }

            return summary;

        } catch (Exception e) {
            throw new RuntimeException("Erro ao chamar a API Gemini: " + e.getMessage(), e);
        }
    }

    public String generateSummary(String textService, String prompt) {
        return generateGenericSummary(textService, prompt);
    }

    public String generateSummary(String textService) {
        String prompt = createSummaryPrompt();
        return generateGenericSummary(textService, prompt);
    }

    private String createSummaryPrompt() {
        return """
            **Instrução Importante:** Analise toda a conversa do início ao fim. 
            Ignore qualquer mensagem enviada pelo bot chamado "Automatico". 
            Considere apenas o cliente e o atendente humano.

            Escreva **tudo em primeira pessoa**, como se **eu**, técnico, estivesse fazendo o summary.
            O resultado deve ser explicito, contextual e seguir *exatamente* o formato abaixo:

            **PROBLEMA / DÚVIDA:** [Descreva em UMA frase, curta porém completa, qual foi o problema ou dúvida principal do cliente. 
            Inclua um contexto mínimo que ajude a entender a situação (ex.: operação que ele tentava fazer, etapa em que o erro ocorreu ou o que estava impedindo a continuação). 
            Use informações críticas somente quando forem realmente necessárias para compreensão do problema.]

            **SOLUÇÃO APRESENTADA:** [Descreva, em primeira pessoa, de forma clara e assertiva, tudo o que eu fiz para resolver o problema. 
            Explique o raciocínio, os passos tomados, verificações realizadas e ajustes aplicados, sempre de forma objetiva e com a resolução final. 
            Se houver documentos fiscais citados — como NF, NFe, Nota, Cupom, CT, CT-e, MDF-e, NFC-e — identifique todos e padronize sempre como: número doc: X. 
            Ignore totalmente números que sejam identificadores de AnyDesk. 
            Considere como AnyDesk qualquer sequência numérica com mais de 5 dígitos que não esteja claramente vinculada a um documento fiscal. 
            Não utilize esses números no summary e não os interprete.

            **OPORTUNIDADE DE UPSELL:** [Responda apenas 'SIM' ou 'NÃO'. 'SIM' somente se houve oportunidade real de VENDA de produto ou serviço. 
            Elogios, avaliações ou conversas neutras NUNCA contam. Se responder 'SIM', descreva o contexto e informe se a venda foi concluída, não concluída ou se ficou em andamento. 
            Se responder 'NÃO', explique claramente o motivo (ex.: cliente não deu abertura, não havia necessidade, não havia contexto para oferta).]

            **PRINTS DE ERRO OU DE MENSAGENS RELEVANTES:** [Responda apenas 'Sim' ou 'Não'.]

            **HUMOR DO CLIENTE:** [Informe em UMA palavra: 'BOM', 'NEUTRO' e 'IRRITADO'.]

            **MÓDULO:** [Selecione APENAS UMA categoria abaixo, escolhendo aquela que melhor representa o tema central do atendimento. 
            Analise o contexto e identifique sobre qual módulo o cliente realmente estava falando. 
            Escolha somente entre estas opções exatas, Se não houver clareza, escolha GENÉRICO.]:

            - NF-E (NOTA FISCAL ELETRÔNICA)
            - NFC-E (NOTA FISCAL DO CONSUMIDOR ELETRÔNICA)
            - MDF-E
            - CT-E
            - FRENTE DE CAIXA
            - CERTIFICADO
            - CONFIGURAÇÃO DE CONTA
            - COMERCIAL/VENDAS
            - ESTOQUE
            - FINANCEIRO
            - BOLETOS
            - MARKETPLACE / LOJA VIRTUAL
            - RESTAURANTE
            - GENÉRICO
            - RELATÓRIO

            Se houver documentos fiscais, considere qual módulo eles representam. Se houver mais de um assunto no chat, escolha o tema predominante.
            """;
    }

    public String ask(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> parts = Map.of("text", prompt);
        Map<String, Object> contents = Map.of("parts", List.of(parts));
        Map<String, Object> body = Map.of("contents", List.of(contents));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        String urlFinal = GEMINI_URL_BASE + apiKey;

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(urlFinal, request, String.class);
            return extractTextGemini(response.getBody());
        } catch (Exception e) {
            throw new RuntimeException("Erro ao enviar requisição para o Gemini: " + e.getMessage());
        }
    }

    private String extractTextGemini(String json) {
        try {
            JsonNode node = new ObjectMapper().readTree(json);
            return node
                    .path("candidates").get(0)
                    .path("content")
                    .path("parts").get(0)
                    .path("text").asText();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao extrair texto da resposta do Gemini: " + e.getMessage());
        }
    }

    public String getPromptSummary() {
        return createSummaryPrompt();
    }
}