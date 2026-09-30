package com.soften.support.gemini_resumo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soften.support.gemini_resumo.config.TypeSafeApiProperties;
import com.soften.support.gemini_resumo.models.dtos.ProductClassificationResponse;
import com.soften.support.gemini_resumo.models.dtos.ProductSuggestionDto;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class JevProductClassificationService {

    private static final Logger log = LoggerFactory.getLogger(JevProductClassificationService.class);

    private static final String DOMAIN_CONTEXT = """
            Somos uma software house brasileira de ERP fiscal e empresarial.
            O objetivo é identificar o Produto interno correto para encaminhar um atendimento.
            Diferencie documentos fiscais pelo contexto real:
            NF-E é nota de mercadorias/produtos e normalmente envolve SEFAZ, DANFE, XML, NCM, CFOP ou ICMS.
            NFS-E é nota de serviços e normalmente envolve prefeitura, ISS, RPS, código/item de serviço, prestador ou tomador.
            NFC-E é nota/cupom ao consumidor e normalmente envolve venda no caixa, consumidor final, CSC ou QR Code.
            CT-E é conhecimento de transporte; MDF-E é manifesto; CIOT está ligado a ANTT/operação e pagamento de frete.
            Não force uma classificação quando o cliente usa termos genéricos sem contexto suficiente.
            """;

    private static final double SINGLE_MIN_PROBABILITY = 0.75d;
    private static final double SINGLE_MIN_GAP = 0.25d;
    private static final double UNCLEAR_MIN_PROBABILITY = 0.40d;
    private static final double VERY_LOW_TOP_PROBABILITY = 0.35d;

    private final TypeSafeApiProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public JevProductClassificationService(
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

    JevProductClassificationService(
            TypeSafeApiProperties properties,
            RestTemplate restTemplate,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public ProductClassificationResponse classify(String conversation) {
        if (!properties.isConfigured()) {
            throw new JevIntegrationException(
                    "A classificação de produto ainda não está configurada.",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "TYPESAFE_API_KEY ausente."
            );
        }

        Map<String, Object> payload = buildPayload(conversation);
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
            ProductClassificationResponse classification = parseResponse(response.getBody(), latencyMs);

            log.info(
                    "Jev product classification completed. mode={}, topProduct={}, topProbability={}, latencyMs={}",
                    classification.mode(),
                    classification.suggestions().isEmpty() ? "none" : classification.suggestions().get(0).productId(),
                    classification.suggestions().isEmpty() ? 0d : classification.suggestions().get(0).probability(),
                    latencyMs
            );

            return classification;
        } catch (HttpStatusCodeException e) {
            int status = e.getStatusCode().value();
            log.warn("Jev upstream returned HTTP {}.", status);
            throw new JevIntegrationException(
                    "Não foi possível identificar o produto agora. Tente novamente.",
                    HttpStatus.BAD_GATEWAY,
                    "Falha HTTP do Jev. status=" + status,
                    e
            );
        } catch (ResourceAccessException e) {
            log.warn("Jev request timed out or could not connect.");
            throw new JevIntegrationException(
                    "A identificação do produto demorou mais que o esperado. Tente novamente.",
                    HttpStatus.GATEWAY_TIMEOUT,
                    "Timeout/conectividade na chamada Jev.",
                    e
            );
        } catch (JevIntegrationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected Jev classification failure.", e);
            throw new JevIntegrationException(
                    "Não foi possível identificar o produto agora. Tente novamente.",
                    HttpStatus.BAD_GATEWAY,
                    "Falha inesperada ao classificar produto.",
                    e
            );
        }
    }

    private Map<String, Object> buildPayload(String conversation) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("context", DOMAIN_CONTEXT);
        state.put("conversation", conversation.trim());

        Map<String, Object> productQuestion = new LinkedHashMap<>();
        productQuestion.put("type", "choice");
        productQuestion.put(
                "instructions",
                "Escolha o Produto interno que melhor representa o assunto principal da conversa. " +
                        "Use somente os critérios fornecidos. Quando não houver contexto suficiente para distinguir, " +
                        "escolha " + ProductCatalog.UNCLEAR_CHOICE + "."
        );
        productQuestion.put("criteria", ProductCatalog.asJevCriteria());

        return Map.of(
                "state", state,
                "model", properties.getModel(),
                "questions", Map.of("product", productQuestion)
        );
    }

    private ProductClassificationResponse parseResponse(String body, long latencyMs) {
        try {
            if (body == null || body.isBlank()) {
                throw invalidResponse("Resposta vazia.");
            }

            JsonNode root = objectMapper.readTree(body);
            JsonNode answer = root.path("answers").path("product");
            JsonNode probabilities = answer.path("probabilities");

            if (!"choice".equals(answer.path("type").asText()) || !probabilities.isObject()) {
                throw invalidResponse("Resposta sem ChoiceAnswer válido.");
            }

            double confidence = answer.path("confidence").asDouble(-1d);
            if (confidence < 0d || confidence > 1d) {
                throw invalidResponse("Confidence ausente ou inválida.");
            }

            double unclearProbability = probabilities.path(ProductCatalog.UNCLEAR_CHOICE).asDouble(0d);

            List<ProductSuggestionDto> suggestions = ProductCatalog.all().stream()
                    .map(product -> new ProductSuggestionDto(
                            product.id(),
                            product.name(),
                            probabilities.path(product.id()).asDouble(0d)
                    ))
                    .sorted(Comparator.comparingDouble(ProductSuggestionDto::probability).reversed())
                    .limit(3)
                    .toList();

            if (suggestions.isEmpty() || suggestions.get(0).probability() <= 0d) {
                throw invalidResponse("Probabilidades de produto ausentes.");
            }

            double top = suggestions.get(0).probability();
            double second = suggestions.size() > 1 ? suggestions.get(1).probability() : 0d;
            String mode;

            if (unclearProbability >= UNCLEAR_MIN_PROBABILITY || top < VERY_LOW_TOP_PROBABILITY) {
                mode = "uncertain";
            } else if (top >= SINGLE_MIN_PROBABILITY && (top - second) >= SINGLE_MIN_GAP) {
                mode = "single";
            } else {
                mode = "multiple";
            }

            return new ProductClassificationResponse(
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
                    "O serviço retornou uma classificação inválida. Tente novamente.",
                    HttpStatus.BAD_GATEWAY,
                    "Falha ao interpretar resposta do Jev.",
                    e
            );
        }
    }

    private JevIntegrationException invalidResponse(String technicalMessage) {
        return new JevIntegrationException(
                "O serviço retornou uma classificação inválida. Tente novamente.",
                HttpStatus.BAD_GATEWAY,
                technicalMessage
        );
    }
}
