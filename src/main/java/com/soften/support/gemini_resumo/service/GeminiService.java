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
    private final GoogleFileSearchService fileSearchService;
    private static final String GEMINI_URL_BASE = "https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash-lite:generateContent?key=";

    public GeminiService(GoogleFileSearchService fileSearchService) {
        this.fileSearchService = fileSearchService;
    }

    @PostConstruct
    public void init() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Chave da Gemini não encontrada. Defina a variável de ambiente GEMINI_API_KEY " +
                            "ou configure gemini.api.key em application.properties");
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
            e.printStackTrace();
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

                **PROBLEMA / DÚVIDA:** [Descreva um resposta que deve SEMPRE ser apenas UMA frase curta, objetiva, com contexto mínimo, mas
                suficiente para entender o problema real enfrentado pelo cliente. A frase deve deixar claro que se trata
                de um erro, dúvida, rejeição, falha ou bloqueio. Não invente nada que não esteja na conversa.
                Identifique o que o cliente estava tentando fazer (somente se isso ajudar a entender o problema),
                o problema encontrado (erro/dúvida/rejeição/falha), e o que foi impedido por esse problema (opcional).
                A frase deve seguir a lógica: "O cliente [tentou fazer X] e enfrentou [erro/dúvida/rejeição Y],
                impedindo [resultado esperado]." Use apenas os trechos necessários. Restrições obrigatórias:
                não use detalhes irrelevantes como IDs, dados longos, prints ou códigos extensos; não descreva a solução;
                não escreva mais de uma frase; não interprete causas profundas que não estejam claramente descritas;
                mantenha conciso (~20 palavras). Em caso de atendimento incompleto, ilegível ou sem problema claro,
                use este fallback: 'O cliente apresentou uma dúvida ou problema, mas o atendimento não fornece detalhes
                suficientes para identificá-lo.' Se houver múltiplos problemas, selecione apenas o principal.]

                **SOLUÇÃO APRESENTADA:** [A resposta deve SEMPRE ser escrita em primeira pessoa, de forma clara,
                 assertiva, objetiva e totalmente fiel ao que ocorreu no atendimento, descrevendo exatamente o que
                 eu fiz, incluindo raciocínio, verificações, testes, conferências, orientações, análises, ajustes,
                 validações e, quando aplicável, a resolução final. A solução deve refletir precisamente o fluxo
                 do atendimento, sem supor ações que não ocorreram. Sempre identificar documentos fiscais citados
                 (NF, NFe, Nota, Cupom, CT, CT-e, MDF-e, NFC-e), padronizando como: "número doc: X", extraindo
                 todos os números de documentos fiscais mesmo que citados informalmente. Ignore completamente
                 qualquer sequência numérica maior que 5 dígitos que não esteja claramente vinculada a documentos
                 fiscais, considerando todas como possíveis IDs de AnyDesk, e nunca inclua ou interprete esses
                 números no texto. Se o atendimento foi resolvido, descreva tudo o que eu fiz até a solução.
                 Se não foi concluído ou a solução depende de ação futura, descreva claramente que o processo
                 ficou pendente e o motivo. Se o atendimento ficou em agendamento, identifique explicitamente que a
                 solução não foi aplicada no momento, capture a data e/ou horário citados e registre que agendei
                 retorno para dar continuidade, sem inventar datas. Se o cliente não pôde prosseguir, estava sem
                 acesso ou dependia de terceiros, descreva a limitação e o combinado para continuidade. Se nada foi
                 resolvido ainda, explique o que eu tentei, o que foi diagnosticado e por que não foi finalizado.
                 Se houve apenas orientação, registre apenas o que foi explicado. Não invente procedimentos, números
                 ou verificações; não descreva ações não realizadas; não utilize prints, gírias ou transcrições
                 desnecessárias; e não resuma falas do cliente, apenas minhas ações. Em caso de atendimento vazio,
                 ilegível ou sem dados suficientes para identificar minhas ações, use o fallback:
                 "Não consegui identificar as ações realizadas no atendimento devido à falta de informações claras."
                 Ao final, retorne SOMENTE o texto da solução apresentada.]

                    **OPORTUNIDADE DE UPSELL:** [Responda apenas 'SIM' ou 'NÃO'. 'SIM' somente se houve oportunidade real de VENDA de produto ou serviço.
                    Elogios, avaliações ou conversas neutras NUNCA contam. Se responder 'SIM', descreva o contexto e informe se a venda foi concluída, não concluída ou se ficou em andamento.
                    Se responder 'NÃO', explique claramente o motivo (ex.: cliente não deu abertura, não havia necessidade, não havia contexto para oferta).]

                    **PRINTS DE ERRO OU DE MENSAGENS RELEVANTES:** [Responda apenas 'Sim' ou 'Não'.]

                **HUMOR DO CLIENTE:** [Informe em UMA palavra: 'BOM.', 'NEUTRO.' e 'IRRITADO.'.]

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

    /**
     * Busca documentação oficial de forma inteligente (sobrecarga com categoria)
     * Retorna uma lista de documentos do Spring AI
     */
    public List<org.springframework.ai.document.Document> buscarDocumentacaoOficialSmart(String query) {
        return buscarDocumentacaoOficialSmart(query, "manuais");
    }

    /**
     * Busca documentação oficial de forma inteligente com filtro de categoria
     * Retorna uma lista de documentos do Spring AI
     */
    public List<org.springframework.ai.document.Document> buscarDocumentacaoOficialSmart(String query,
            String categoria) {
        try {
            System.out.println("🔍 Busca Smart de documentação [" + categoria + "] para: " + query);

            String systemInstruction = String.format("""
                    Você é um assistente especializado em documentação técnica para o módulo/categoria: '%s'.
                    Retorne apenas documentação oficial e relevante para a consulta fornecida.
                    Ignore conteúdos que não sejam relacionados ao suporte técnico ou manuais de uso.
                    """, categoria != null ? categoria : "Geral");

            String searchQuery = "Recupere os documentos brutos para o termo: " + query;
            String searchResult = fileSearchService.searchManuals(searchQuery, systemInstruction);

            // Cria um documento Spring AI com o resultado
            List<org.springframework.ai.document.Document> documents = new java.util.ArrayList<>();

            if (searchResult != null && !searchResult.isEmpty() &&
                    !searchResult.contains("Nenhuma correspondência") &&
                    !searchResult.contains("Erro")) {

                org.springframework.ai.document.Document doc = new org.springframework.ai.document.Document(
                        "doc-" + System.currentTimeMillis(),
                        searchResult,
                        Map.of(
                                "source", "Google File Search",
                                "query", query,
                                "categoria", categoria != null ? categoria : "N/A",
                                "timestamp", System.currentTimeMillis()));
                documents.add(doc);
                System.out.println("✅ Documento criado com sucesso");
            } else {
                System.out.println("⚠️ Nenhuma documentação encontrada");
            }

            return documents;
        } catch (Exception e) {
            System.err.println("❌ Erro na busca smart: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Erro na busca smart de documentação: " + e.getMessage());
        }
    }
}
