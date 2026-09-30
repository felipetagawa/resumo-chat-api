package com.soften.support.gemini_resumo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soften.support.gemini_resumo.config.TypeSafeApiProperties;
import com.soften.support.gemini_resumo.models.dtos.DocumentationCandidateDto;
import com.soften.support.gemini_resumo.models.dtos.DocumentationClassificationResponse;
import com.soften.support.gemini_resumo.models.dtos.DocumentationSuggestionDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class JevDocumentationClassificationService {

    public static final String UNCLEAR_CHOICE = "__UNCLEAR__";
    public static final int MAX_CANDIDATES = 240;
    private static final int MAX_CONTEXT_CHARS = 8_000;

    private static final double SINGLE_MIN_PROBABILITY = 0.75d;
    private static final double SINGLE_MIN_GAP = 0.25d;
    private static final double UNCLEAR_MIN_PROBABILITY = 0.40d;
    private static final double VERY_LOW_TOP_PROBABILITY = 0.35d;
    private static final double DISTRIBUTION_MIN_SUM = 0.98d;
    private static final double DISTRIBUTION_MAX_SUM = 1.02d;

    private static final Logger log = LoggerFactory.getLogger(JevDocumentationClassificationService.class);

    private final TypeSafeApiProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public JevDocumentationClassificationService(
            TypeSafeApiProperties properties,
            RestTemplateBuilder builder,
            ObjectMapper objectMapper
    ) {
        this(
                properties,
                builder
                        .setConnectTimeout(Duration.ofMillis(properties.getSafeConnectTimeoutMillis()))
                        .setReadTimeout(Duration.ofMillis(properties.getSafeReadTimeoutMillis()))
                        .build(),
                objectMapper
        );
    }

    JevDocumentationClassificationService(
            TypeSafeApiProperties properties,
            RestTemplate restTemplate,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public DocumentationClassificationResponse classify(
            String context,
            List<DocumentationCandidateDto> candidates
    ) {
        if (!properties.isConfigured()) {
            throw new JevIntegrationException(
                    "A sugestão de documentação ainda não está configurada.",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "TYPESAFE_API_KEY ausente."
            );
        }

        String normalizedContext = context == null ? "" : context.trim();
        if (normalizedContext.isBlank()) {
            throw new IllegalArgumentException("Campo 'context' é obrigatório e não pode estar vazio.");
        }
        if (normalizedContext.length() > MAX_CONTEXT_CHARS) {
            throw new IllegalArgumentException("Campo 'context' excede o limite de " + MAX_CONTEXT_CHARS + " caracteres.");
        }

        List<DocumentationCandidateDto> normalizedCandidates = normalizeCandidates(candidates);
        Map<String, Object> payload = buildPayload(normalizedContext, normalizedCandidates);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getKey());

        long startedAt = System.nanoTime();

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    properties.getSystemOneUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    String.class
            );

            long latencyMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            DocumentationClassificationResponse result =
                    parseResponse(response.getBody(), normalizedCandidates, latencyMs);

            log.info(
                    "Jev documentation classification completed. mode={}, candidates={}, latencyMs={}",
                    result.mode(),
                    normalizedCandidates.size(),
                    latencyMs
            );

            return result;
        } catch (HttpStatusCodeException e) {
            log.warn("Jev documentation upstream returned HTTP {}.", e.getStatusCode().value());
            throw new JevIntegrationException(
                    "Não foi possível sugerir a documentação agora. Tente novamente.",
                    HttpStatus.BAD_GATEWAY,
                    "Falha HTTP do Jev na documentação. status=" + e.getStatusCode().value(),
                    e
            );
        } catch (ResourceAccessException e) {
            throw new JevIntegrationException(
                    "A sugestão de documentação demorou mais que o esperado. Tente novamente.",
                    HttpStatus.GATEWAY_TIMEOUT,
                    "Timeout/conectividade na chamada Jev de documentação.",
                    e
            );
        } catch (JevIntegrationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected documentation classification failure.", e);
            throw new JevIntegrationException(
                    "Não foi possível sugerir a documentação agora. Tente novamente.",
                    HttpStatus.BAD_GATEWAY,
                    "Falha inesperada ao classificar documentação.",
                    e
            );
        }
    }

    private List<DocumentationCandidateDto> normalizeCandidates(List<DocumentationCandidateDto> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("Informe ao menos uma documentação candidata.");
        }
        if (candidates.size() > MAX_CANDIDATES) {
            throw new IllegalArgumentException(
                    "O limite é de " + MAX_CANDIDATES + " documentações candidatas por classificação."
            );
        }

        Map<String, DocumentationCandidateDto> unique = new LinkedHashMap<>();
        for (DocumentationCandidateDto candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            String id = candidate.id() == null ? "" : candidate.id().trim();
            String label = candidate.label() == null ? "" : candidate.label().trim();

            if (id.isBlank() || label.isBlank()) {
                throw new IllegalArgumentException("Toda documentação candidata precisa de id e label.");
            }
            if (UNCLEAR_CHOICE.equals(id)) {
                throw new IllegalArgumentException("ID de documentação reservado: " + UNCLEAR_CHOICE);
            }
            if (unique.putIfAbsent(id, new DocumentationCandidateDto(id, label)) != null) {
                throw new IllegalArgumentException("IDs de documentação duplicados não são permitidos.");
            }
        }

        if (unique.isEmpty()) {
            throw new IllegalArgumentException("Informe ao menos uma documentação candidata válida.");
        }
        return List.copyOf(unique.values());
    }

    private Map<String, Object> buildPayload(
            String context,
            List<DocumentationCandidateDto> candidates
    ) {
        Map<String, Object> criteria = new LinkedHashMap<>();
        for (DocumentationCandidateDto candidate : candidates) {
            criteria.put(candidate.id(), candidate.label());
        }
        criteria.put(
                UNCLEAR_CHOICE,
                "Nenhuma das documentações candidatas descreve com segurança o problema ou dúvida relatado."
        );

        Map<String, Object> question = new LinkedHashMap<>();
        question.put("type", "choice");
        question.put(
                "instructions",
                "Escolha a documentação oficial que melhor representa o problema ou dúvida principal do atendimento. " +
                        "Use apenas as opções fornecidas. Não escolha por semelhança superficial quando faltar evidência; " +
                        "nesse caso escolha " + UNCLEAR_CHOICE + "."
        );
        question.put("criteria", criteria);

        return Map.of(
                "state", Map.of(
                        "context",
                        "Suporte técnico de uma software house brasileira de ERP fiscal e empresarial.",
                        "atendimento",
                        context
                ),
                "model", properties.getModel(),
                "questions", Map.of("documentation", question)
        );
    }

    private DocumentationClassificationResponse parseResponse(
            String body,
            List<DocumentationCandidateDto> candidates,
            long latencyMs
    ) {
        try {
            if (body == null || body.isBlank()) {
                throw invalidResponse("Resposta vazia.");
            }

            JsonNode answer = objectMapper.readTree(body).path("answers").path("documentation");
            JsonNode probabilities = answer.path("probabilities");

            if (!"choice".equals(answer.path("type").asText()) || !probabilities.isObject()) {
                throw invalidResponse("Resposta sem ChoiceAnswer válido.");
            }

            double confidence = answer.path("confidence").asDouble(-1d);
            if (!Double.isFinite(confidence) || confidence < 0d || confidence > 1d) {
                throw invalidResponse("Confidence ausente ou inválida.");
            }

            Set<String> expectedChoices = candidates.stream()
                    .map(DocumentationCandidateDto::id)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            expectedChoices.add(UNCLEAR_CHOICE);

            if (probabilities.size() != expectedChoices.size()) {
                throw invalidResponse("Distribuição de probabilidades incompleta.");
            }

            Map<String, Double> validated = new LinkedHashMap<>();
            double sum = 0d;
            for (String id : expectedChoices) {
                JsonNode node = probabilities.get(id);
                if (node == null || !node.isNumber()) {
                    throw invalidResponse("Probabilidade ausente para a opção " + id + ".");
                }
                double value = node.asDouble();
                if (!Double.isFinite(value) || value < 0d || value > 1d) {
                    throw invalidResponse("Probabilidade inválida para a opção " + id + ".");
                }
                validated.put(id, value);
                sum += value;
            }

            if (sum < DISTRIBUTION_MIN_SUM || sum > DISTRIBUTION_MAX_SUM) {
                throw invalidResponse("Distribuição de probabilidades não soma aproximadamente 1.");
            }

            String selectedChoice = answer.path("choice").asText("");
            if (!expectedChoices.contains(selectedChoice)) {
                throw invalidResponse("Choice retornado não pertence às documentações enviadas.");
            }

            double maxProbability = validated.values().stream()
                    .mapToDouble(Double::doubleValue)
                    .max()
                    .orElseThrow();
            if (validated.get(selectedChoice) + 1e-9d < maxProbability) {
                throw invalidResponse("Choice retornado não corresponde à maior probabilidade.");
            }

            Map<String, DocumentationCandidateDto> byId = candidates.stream()
                    .collect(Collectors.toMap(
                            DocumentationCandidateDto::id,
                            candidate -> candidate,
                            (a, b) -> a,
                            LinkedHashMap::new
                    ));

            List<DocumentationSuggestionDto> ranked = candidates.stream()
                    .map(candidate -> new DocumentationSuggestionDto(
                            candidate.id(),
                            candidate.label(),
                            validated.get(candidate.id())
                    ))
                    .filter(suggestion -> suggestion.probability() > 0d)
                    .sorted(Comparator.comparingDouble(DocumentationSuggestionDto::probability).reversed())
                    .toList();

            double unclearProbability = validated.get(UNCLEAR_CHOICE);
            double top = ranked.isEmpty() ? 0d : ranked.get(0).probability();
            double second = ranked.size() > 1 ? ranked.get(1).probability() : 0d;

            String mode;
            if (unclearProbability >= UNCLEAR_MIN_PROBABILITY || top < VERY_LOW_TOP_PROBABILITY) {
                mode = "uncertain";
            } else if (top >= SINGLE_MIN_PROBABILITY && (top - second) >= SINGLE_MIN_GAP) {
                mode = "single";
            } else {
                mode = "multiple";
            }

            List<DocumentationSuggestionDto> suggestions;
            if ("single".equals(mode) && !ranked.isEmpty()) {
                suggestions = List.of(ranked.get(0));
            } else {
                suggestions = ranked.stream().limit(3).toList();
            }

            return new DocumentationClassificationResponse(
                    mode,
                    suggestions,
                    confidence,
                    unclearProbability,
                    latencyMs
            );
        } catch (JevIntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw new JevIntegrationException(
                    "O serviço retornou uma sugestão de documentação inválida. Tente novamente.",
                    HttpStatus.BAD_GATEWAY,
                    "Falha ao interpretar resposta Jev de documentação.",
                    e
            );
        }
    }

    private JevIntegrationException invalidResponse(String technicalMessage) {
        return new JevIntegrationException(
                "O serviço retornou uma sugestão de documentação inválida. Tente novamente.",
                HttpStatus.BAD_GATEWAY,
                technicalMessage
        );
    }
}
