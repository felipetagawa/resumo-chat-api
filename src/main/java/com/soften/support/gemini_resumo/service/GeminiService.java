package com.soften.support.gemini_resumo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soften.support.gemini_resumo.config.GeminiApiProperties;
import jakarta.annotation.PostConstruct;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class GeminiService {
    public static final int PROMPT_COMPLEMENT_MAX_LENGTH = 2000;
    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);
    private static final String TEMPORARY_UNAVAILABLE_MESSAGE =
            "O serviço de geração de resumo está temporariamente indisponível. Tente novamente em alguns instantes.";
    private static final String REQUEST_FAILED_MESSAGE =
            "Não foi possível concluir a geração de resumo no momento.";

    private final GeminiApiProperties properties;
    private final RestTemplate restTemplate;
    private final GoogleFileSearchService fileSearchService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeminiService(GeminiApiProperties properties,
                         RestTemplate geminiRestTemplate,
                         GoogleFileSearchService fileSearchService) {
        this.properties = properties;
        this.restTemplate = geminiRestTemplate;
        this.fileSearchService = fileSearchService;
    }

    @PostConstruct
    public void init() {
        if (properties.getKey() == null || properties.getKey().isBlank()) {
            throw new IllegalStateException(
                    "Chave da Gemini não encontrada. Defina a variável de ambiente GEMINI_API_KEY " +
                            "ou configure gemini.api.key em application.properties");
        }
    }

    private String generateGenericSummary(String textService, String promptComplement, String contextPrompt) {
        String prompt = contextPrompt + "\n\n" + buildSummaryInput(textService, promptComplement);
        JSONObject body = buildGenerateContentBody(prompt);
        String respBody = executeGenerateContent(body);
        return extractSummary(respBody);
    }

    public String generateSummary(String textService) {
        String prompt = createSummaryPrompt();
        return generateGenericSummary(textService, null, prompt);
    }

    public String generateSummary(String textService, String promptComplement) {
        String prompt = createSummaryPrompt();
        return generateGenericSummary(textService, promptComplement, prompt);
    }

    public String validateAndNormalizePromptComplement(String promptComplement) {
        if (promptComplement == null) {
            return null;
        }

        String normalized = promptComplement.trim();
        if (normalized.isBlank()) {
            return null;
        }

        if (normalized.length() > PROMPT_COMPLEMENT_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Campo 'promptComplement' excede o limite de " + PROMPT_COMPLEMENT_MAX_LENGTH + " caracteres.");
        }
        return normalized;
    }

    String createSummaryPromptWithComplement(String promptComplement) {
        String basePrompt = createSummaryPrompt();
        String normalizedComplement = validateAndNormalizePromptComplement(promptComplement);
        if (normalizedComplement == null) {
            return basePrompt;
        }

        return basePrompt
                + "\n\nINSTRUCOES COMPLEMENTARES DO USUARIO:\n"
                + normalizedComplement;
    }

    private String createSummaryPrompt() {
        return """
                    **Instrução Importante:** Analise toda a conversa do início ao fim.
                    Ignore qualquer mensagem enviada pelo bot chamado "Automatico".
                    Considere apenas o cliente e o atendente humano.
                    Se houver a seção "COMPLEMENTO INFORMADO PELO ATENDENTE", trate esse conteúdo como contexto adicional confiável fornecido pelo atendente.
                    Não trate esse complemento como fala do cliente.

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

    private String buildSummaryInput(String textService, String promptComplement) {
        StringBuilder input = new StringBuilder();
        input.append("ATENDIMENTO ANALISADO:\n")
                .append(textService)
                .append("\n");

        if (promptComplement != null && !promptComplement.isBlank()) {
            input.append("\nCOMPLEMENTO INFORMADO PELO ATENDENTE:\n")
                    .append(promptComplement.trim())
                    .append("\n");
        }

        return input.toString();
    }

    public String ask(String prompt) {
        JSONObject body = buildGenerateContentBody(prompt);
        String response = executeGenerateContent(body);
        return extractTextGemini(response);
    }

    private String extractTextGemini(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return node
                    .path("candidates").get(0)
                    .path("content")
                    .path("parts").get(0)
                    .path("text").asText();
        } catch (Exception e) {
            throw new GeminiIntegrationException(
                    REQUEST_FAILED_MESSAGE,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Falha ao extrair o texto retornado pelo Gemini.",
                    e);
        }
    }

    private JSONObject buildGenerateContentBody(String prompt) {
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
        return body;
    }

    private String executeGenerateContent(JSONObject body) {
        Instant overallStart = Instant.now();
        int maxAttempts = properties.getSafeMaxAttempts();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);

        log.info("Iniciando chamada Gemini generateContent. model={}, maxAttempts={}",
                properties.getModel(), maxAttempts);

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            Instant attemptStart = Instant.now();
            try {
                log.info("Chamando Gemini. model={}, attempt={}/{}",
                        properties.getModel(), attempt, maxAttempts);
                ResponseEntity<String> response = restTemplate.postForEntity(buildGenerateContentUrl(), entity, String.class);
                long attemptMillis = Duration.between(attemptStart, Instant.now()).toMillis();
                log.info("Resposta Gemini recebida com sucesso. model={}, attempt={}, status={}, durationMs={}",
                        properties.getModel(), attempt, response.getStatusCode().value(), attemptMillis);
                return requireResponseBody(response.getBody());
            } catch (HttpStatusCodeException e) {
                HttpStatusCode status = e.getStatusCode();
                long attemptMillis = Duration.between(attemptStart, Instant.now()).toMillis();
                boolean retryable = isRetryableStatus(status);
                log.warn("Falha HTTP ao chamar Gemini. model={}, attempt={}, status={}, retryable={}, durationMs={}",
                        properties.getModel(), attempt, status.value(), retryable, attemptMillis);

                if (retryable && attempt < maxAttempts) {
                    long delayMillis = resolveDelayMillis(attempt, status, e.getResponseHeaders());
                    log.info("Agendando nova tentativa Gemini. model={}, nextAttempt={}, delayMs={}",
                            properties.getModel(), attempt + 1, delayMillis);
                    sleepBeforeRetry(delayMillis);
                    continue;
                }

                long totalMillis = Duration.between(overallStart, Instant.now()).toMillis();
                if (retryable) {
                    log.error("Tentativas esgotadas ao chamar Gemini. model={}, attempts={}, lastStatus={}, totalDurationMs={}",
                            properties.getModel(), attempt, status.value(), totalMillis);
                    throw new GeminiIntegrationException(
                            TEMPORARY_UNAVAILABLE_MESSAGE,
                            HttpStatus.BAD_GATEWAY,
                            "Gemini indisponivel apos retries. status=" + status.value(),
                            e);
                }

                log.error("Falha nao transitoria ao chamar Gemini. model={}, status={}, totalDurationMs={}",
                        properties.getModel(), status.value(), totalMillis);
                throw new GeminiIntegrationException(
                        REQUEST_FAILED_MESSAGE,
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Falha nao transitoria retornada pelo Gemini. status=" + status.value(),
                        e);
            } catch (ResourceAccessException e) {
                long attemptMillis = Duration.between(attemptStart, Instant.now()).toMillis();
                boolean retryable = isRetryableResourceAccess(e);
                log.warn("Falha de conectividade ao chamar Gemini. model={}, attempt={}, retryable={}, durationMs={}, errorType={}",
                        properties.getModel(), attempt, retryable, attemptMillis, rootCauseName(e));

                if (retryable && attempt < maxAttempts) {
                    long delayMillis = resolveDelayMillis(attempt, null, null);
                    log.info("Agendando nova tentativa Gemini apos falha de conectividade. model={}, nextAttempt={}, delayMs={}",
                            properties.getModel(), attempt + 1, delayMillis);
                    sleepBeforeRetry(delayMillis);
                    continue;
                }

                long totalMillis = Duration.between(overallStart, Instant.now()).toMillis();
                if (retryable) {
                    log.error("Tentativas esgotadas por falhas de conectividade. model={}, attempts={}, totalDurationMs={}",
                            properties.getModel(), attempt, totalMillis);
                    throw new GeminiIntegrationException(
                            TEMPORARY_UNAVAILABLE_MESSAGE,
                            HttpStatus.BAD_GATEWAY,
                            "Falha temporaria de conectividade com o Gemini apos retries.",
                            e);
                }

                log.error("Falha nao transitoria de conectividade ao chamar Gemini. model={}, totalDurationMs={}, errorType={}",
                        properties.getModel(), totalMillis, rootCauseName(e));
                throw new GeminiIntegrationException(
                        REQUEST_FAILED_MESSAGE,
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Falha nao transitoria de conectividade ao chamar Gemini.",
                        e);
            } catch (GeminiIntegrationException e) {
                throw e;
            } catch (Exception e) {
                long totalMillis = Duration.between(overallStart, Instant.now()).toMillis();
                log.error("Erro inesperado ao chamar Gemini. model={}, totalDurationMs={}, errorType={}",
                        properties.getModel(), totalMillis, e.getClass().getSimpleName());
                throw new GeminiIntegrationException(
                        REQUEST_FAILED_MESSAGE,
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Erro inesperado ao chamar o Gemini.",
                        e);
            }
        }

        throw new GeminiIntegrationException(
                REQUEST_FAILED_MESSAGE,
                HttpStatus.INTERNAL_SERVER_ERROR,
                new IllegalStateException("Loop de retry finalizado sem retorno."));
    }

    private String extractSummary(String respBody) {
        try {
            JSONObject json = new JSONObject(respBody);
            String finishReason = json
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .optString("finishReason", null);

            if ("MAX_TOKENS".equalsIgnoreCase(finishReason)) {
                throw new GeminiIntegrationException(
                        REQUEST_FAILED_MESSAGE,
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        new IllegalStateException("Resposta do Gemini excedeu o limite de tokens."));
            }

            String summary = json
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text");

            if (summary == null || summary.isBlank()) {
                throw new GeminiIntegrationException(
                        REQUEST_FAILED_MESSAGE,
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        new IllegalStateException("Resumo retornado pelo Gemini veio vazio."));
            }

            return summary;
        } catch (GeminiIntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw new GeminiIntegrationException(
                    REQUEST_FAILED_MESSAGE,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Falha ao interpretar a resposta do Gemini.",
                    e);
        }
    }

    private String requireResponseBody(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new GeminiIntegrationException(
                    REQUEST_FAILED_MESSAGE,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    new IllegalStateException("Resposta vazia da API Gemini."));
        }
        return responseBody;
    }

    private boolean isRetryableStatus(HttpStatusCode status) {
        int statusCode = status.value();
        return statusCode == 429 || statusCode == 502 || statusCode == 503 || statusCode == 504;
    }

    private boolean isRetryableResourceAccess(ResourceAccessException exception) {
        Throwable rootCause = exception.getMostSpecificCause();
        return rootCause instanceof SocketTimeoutException || rootCause instanceof ConnectException;
    }

    private long resolveDelayMillis(int attempt, HttpStatusCode status, HttpHeaders headers) {
        if (status != null && status.value() == 429 && headers != null) {
            long retryAfterMillis = extractRetryAfterMillis(headers);
            if (retryAfterMillis > 0) {
                return retryAfterMillis;
            }
        }

        double exponentialFactor = Math.pow(properties.getSafeBackoffMultiplier(), Math.max(attempt - 1, 0));
        long computedDelay = (long) (properties.getSafeInitialDelayMillis() * exponentialFactor);
        return Math.max(computedDelay, 0L);
    }

    private void sleepBeforeRetry(long delayMillis) {
        if (delayMillis <= 0) {
            return;
        }

        try {
            TimeUnit.MILLISECONDS.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GeminiIntegrationException(
                    TEMPORARY_UNAVAILABLE_MESSAGE,
                    HttpStatus.BAD_GATEWAY,
                    "Thread interrompida durante o backoff do retry.",
                    e);
        }
    }

    private String buildGenerateContentUrl() {
        return properties.getGenerateContentBaseUrl()
                + "/"
                + properties.getModel()
                + ":generateContent?key="
                + properties.getKey();
    }

    private String rootCauseName(Exception e) {
        Throwable rootCause = e;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        return rootCause != null ? rootCause.getClass().getSimpleName() : e.getClass().getSimpleName();
    }

    private long extractRetryAfterMillis(HttpHeaders headers) {
        String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfter == null || retryAfter.isBlank()) {
            return -1L;
        }

        try {
            long retryAfterMillis = Long.parseLong(retryAfter.trim()) * 1000L;
            if (retryAfterMillis <= 0) {
                return -1L;
            }

            long maxRetryAfterMillis = properties.getSafeMaxRetryAfterMillis();
            if (maxRetryAfterMillis > 0) {
                return Math.min(retryAfterMillis, maxRetryAfterMillis);
            }

            return retryAfterMillis;
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    public String getPromptSummary() { return createSummaryPrompt(); }

    public List<org.springframework.ai.document.Document> buscarDocumentacaoOficialSmart(String query) {
        return buscarDocumentacaoOficialSmart(query, "manuais");
    }

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
