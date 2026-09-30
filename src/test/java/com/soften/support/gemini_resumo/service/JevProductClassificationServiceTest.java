package com.soften.support.gemini_resumo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soften.support.gemini_resumo.config.TypeSafeApiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class JevProductClassificationServiceTest {

    private TypeSafeApiProperties properties;
    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private JevProductClassificationService service;

    @BeforeEach
    void setUp() {
        properties = new TypeSafeApiProperties();
        properties.setKey("test-key");
        properties.setModel("jev-latest");
        properties.setSystemOneUrl("https://api.typesafe.ai/v1/systemone");

        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        service = new JevProductClassificationService(properties, restTemplate, new ObjectMapper());
    }

    @Test
    void clearClassificationReturnsSingleMode() {
        String body = """
                {
                  "model": "jev-latest",
                  "answers": {
                    "product": {
                      "type": "choice",
                      "choice": "21",
                      "confidence": 0.95,
                      "probabilities": {
                        "21": 0.82,
                        "44": 0.07,
                        "1": 0.05,
                        "__UNCLEAR__": 0.02
                      }
                    }
                  },
                  "usage": {"input_tokens": 500, "output_tokens": 20}
                }
                """;

        server.expect(once(), requestTo(properties.getSystemOneUrl()))
                .andExpect(method(POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        var result = service.classify("Cliente informa erro ao emitir cupom fiscal NFC-e.");

        assertEquals("single", result.mode());
        assertEquals("21", result.suggestions().get(0).productId());
        assertEquals(0.82d, result.suggestions().get(0).probability(), 0.0001d);
        server.verify();
    }

    @Test
    void balancedClassificationReturnsTopThree() {
        String body = """
                {
                  "model": "jev-latest",
                  "answers": {
                    "product": {
                      "type": "choice",
                      "choice": "44",
                      "confidence": 0.61,
                      "probabilities": {
                        "44": 0.48,
                        "1": 0.31,
                        "21": 0.17,
                        "__UNCLEAR__": 0.03
                      }
                    }
                  },
                  "usage": {"input_tokens": 450, "output_tokens": 18}
                }
                """;

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
    void unclearChoiceMarksResponseAsUncertain() {
        String body = """
                {
                  "model": "jev-latest",
                  "answers": {
                    "product": {
                      "type": "choice",
                      "choice": "__UNCLEAR__",
                      "confidence": 0.88,
                      "probabilities": {
                        "__UNCLEAR__": 0.55,
                        "1": 0.20,
                        "44": 0.15,
                        "21": 0.06
                      }
                    }
                  },
                  "usage": {"input_tokens": 300, "output_tokens": 15}
                }
                """;

        server.expect(once(), requestTo(properties.getSystemOneUrl()))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        var result = service.classify("Cliente: está dando erro.");

        assertEquals("uncertain", result.mode());
        assertTrue(result.unclearProbability() >= 0.55d);
        server.verify();
    }

    @Test
    void missingApiKeyFailsWithoutCallingUpstream() {
        properties.setKey("");
        JevProductClassificationService unconfigured =
                new JevProductClassificationService(properties, restTemplate, new ObjectMapper());

        JevIntegrationException error = assertThrows(
                JevIntegrationException.class,
                () -> unconfigured.classify("alguma conversa")
        );

        assertEquals(503, error.getHttpStatus().value());
    }

    @Test
    void invalidUpstreamResponseIsRejected() {
        server.expect(once(), requestTo(properties.getSystemOneUrl()))
                .andRespond(withSuccess("{"answers":{}}", MediaType.APPLICATION_JSON));

        assertThrows(
                JevIntegrationException.class,
                () -> service.classify("alguma conversa")
        );
        server.verify();
    }
}
