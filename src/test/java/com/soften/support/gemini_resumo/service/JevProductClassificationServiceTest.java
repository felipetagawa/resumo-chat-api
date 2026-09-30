package com.soften.support.gemini_resumo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.soften.support.gemini_resumo.config.TypeSafeApiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class JevProductClassificationServiceTest {

    private TypeSafeApiProperties properties;
    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private ObjectMapper objectMapper;
    private JevProductClassificationService service;

    @BeforeEach
    void setUp() {
        properties = new TypeSafeApiProperties();
        properties.setKey("test-key");
        properties.setModel("jev-latest");
        properties.setSystemOneUrl("https://api.typesafe.ai/v1/systemone");

        objectMapper = new ObjectMapper();
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        service = new JevProductClassificationService(properties, restTemplate, objectMapper);
    }

    @Test
    void clearClassificationReturnsSingleMode() throws Exception {
        String body = jevResponse(
                "21",
                0.95d,
                Map.of(
                        "21", 0.84d,
                        "44", 0.07d,
                        "1", 0.05d,
                        "4", 0.02d,
                        ProductCatalog.UNCLEAR_CHOICE, 0.02d
                )
        );

        server.expect(once(), requestTo(properties.getSystemOneUrl()))
                .andExpect(method(POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        var result = service.classify("Cliente informa erro ao emitir cupom fiscal NFC-e.");

        assertEquals("single", result.mode());
        assertEquals(1, result.suggestions().size());
        assertEquals("21", result.suggestions().get(0).productId());
        assertEquals(0.84d, result.suggestions().get(0).probability(), 0.0001d);
        server.verify();
    }

    @Test
    void balancedClassificationReturnsTopThree() throws Exception {
        String body = jevResponse(
                "44",
                0.61d,
                Map.of(
                        "44", 0.48d,
                        "1", 0.31d,
                        "21", 0.17d,
                        "4", 0.01d,
                        ProductCatalog.UNCLEAR_CHOICE, 0.03d
                )
        );

        server.expect(once(), requestTo(properties.getSystemOneUrl()))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        var result = service.classify("Cliente informa apenas que está com erro na nota.");

        assertEquals("multiple", result.mode());
        assertEquals(3, result.suggestions().size());
        assertEquals("44", result.suggestions().get(0).productId());
        assertEquals("1", result.suggestions().get(1).productId());
        assertEquals("21", result.suggestions().get(2).productId());
        server.verify();
    }

    @Test
    void unclearChoiceMarksResponseAsUncertain() throws Exception {
        String body = jevResponse(
                ProductCatalog.UNCLEAR_CHOICE,
                0.88d,
                Map.of(
                        ProductCatalog.UNCLEAR_CHOICE, 0.55d,
                        "1", 0.20d,
                        "44", 0.15d,
                        "21", 0.06d,
                        "4", 0.04d
                )
        );

        server.expect(once(), requestTo(properties.getSystemOneUrl()))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        var result = service.classify("Cliente: está dando erro.");

        assertEquals("uncertain", result.mode());
        assertTrue(result.unclearProbability() >= 0.55d);
        server.verify();
    }

    @Test
    void pureUnclearAbstentionIsValid() throws Exception {
        String body = jevResponse(
                ProductCatalog.UNCLEAR_CHOICE,
                0.99d,
                Map.of(ProductCatalog.UNCLEAR_CHOICE, 1.0d)
        );

        server.expect(once(), requestTo(properties.getSystemOneUrl()))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        var result = service.classify("Cliente: preciso de ajuda.");

        assertEquals("uncertain", result.mode());
        assertEquals(1.0d, result.unclearProbability(), 0.0001d);
        assertTrue(result.suggestions().isEmpty());
        server.verify();
    }

    @Test
    void missingApiKeyFailsWithoutCallingUpstream() {
        properties.setKey("");
        JevProductClassificationService unconfigured =
                new JevProductClassificationService(properties, restTemplate, objectMapper);

        JevIntegrationException error = assertThrows(
                JevIntegrationException.class,
                () -> unconfigured.classify("alguma conversa")
        );

        assertEquals(503, error.getHttpStatus().value());
    }

    @Test
    void incompleteProbabilityMapIsRejected() throws Exception {
        ObjectNode root = objectMapper.readTree(
                jevResponse("1", 0.80d, Map.of("1", 1.0d))
        ).deepCopy();

        ((ObjectNode) root.path("answers").path("product").path("probabilities")).remove("4");

        server.expect(once(), requestTo(properties.getSystemOneUrl()))
                .andRespond(withSuccess(objectMapper.writeValueAsString(root), MediaType.APPLICATION_JSON));

        assertThrows(
                JevIntegrationException.class,
                () -> service.classify("alguma conversa")
        );
        server.verify();
    }

    @Test
    void invalidUpstreamResponseIsRejected() {
        server.expect(once(), requestTo(properties.getSystemOneUrl()))
                .andRespond(withSuccess("{\"answers\":{}}", MediaType.APPLICATION_JSON));

        assertThrows(
                JevIntegrationException.class,
                () -> service.classify("alguma conversa")
        );
        server.verify();
    }

    private String jevResponse(
            String choice,
            double confidence,
            Map<String, Double> overrides
    ) throws Exception {
        Map<String, Double> probabilities = new LinkedHashMap<>();
        for (ProductCatalog.ProductDefinition product : ProductCatalog.all()) {
            probabilities.put(product.id(), 0d);
        }
        probabilities.put(ProductCatalog.UNCLEAR_CHOICE, 0d);
        probabilities.putAll(overrides);

        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("type", "choice");
        answer.put("choice", choice);
        answer.put("confidence", confidence);
        answer.put("probabilities", probabilities);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", "jev-latest");
        payload.put("answers", Map.of("product", answer));
        payload.put("usage", Map.of("input_tokens", 300, "output_tokens", 15));

        return objectMapper.writeValueAsString(payload);
    }
}
