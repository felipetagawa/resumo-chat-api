package com.soften.support.gemini_resumo.service;

import com.soften.support.gemini_resumo.config.GeminiApiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiServiceTest {

    @Test
    void createSummaryPromptWithComplementShouldKeepBasePromptWhenComplementMissing() {
        GeminiService service = newService(new RestTemplate(), defaultProperties());

        String prompt = service.createSummaryPromptWithComplement(null);

        assertNotNull(prompt);
        assertFalse(prompt.contains("INSTRUCOES COMPLEMENTARES DO USUARIO"));
    }

    @Test
    void createSummaryPromptWithComplementShouldAppendComplementSection() {
        GeminiService service = newService(new RestTemplate(), defaultProperties());

        String prompt = service.createSummaryPromptWithComplement("  foco em concisao  ");

        assertTrue(prompt.contains("INSTRUCOES COMPLEMENTARES DO USUARIO"));
        assertTrue(prompt.contains("foco em concisao"));
    }

    @Test
    void validateAndNormalizePromptComplementShouldIgnoreBlank() {
        GeminiService service = newService(new RestTemplate(), defaultProperties());

        String normalized = service.validateAndNormalizePromptComplement("   ");

        assertNull(normalized);
    }

    @Test
    void validateAndNormalizePromptComplementShouldRejectOversized() {
        GeminiService service = newService(new RestTemplate(), defaultProperties());

        assertThrows(IllegalArgumentException.class,
                () -> service.validateAndNormalizePromptComplement("x".repeat(GeminiService.PROMPT_COMPLEMENT_MAX_LENGTH + 1)));
    }

    @Test
    void generateSummaryShouldSucceedOnFirstAttempt() {
        RestTemplate restTemplate = new RestTemplate();
        GeminiService service = newService(restTemplate, defaultProperties());
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        server.expect(once(), method(HttpMethod.POST))
                .andRespond(withSuccess(geminiResponse("summary"), MediaType.APPLICATION_JSON));

        String summary = service.generateSummary("chat content");

        assertEquals("summary", summary);
        server.verify();
    }

    @Test
    void generateSummaryShouldRetryAfterSingle503AndThenSucceed() {
        RestTemplate restTemplate = new RestTemplate();
        GeminiApiProperties properties = defaultProperties();
        properties.setInitialDelayMillis(1);
        GeminiService service = newService(restTemplate, properties);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        server.expect(times(1), method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON));
        server.expect(times(1), method(HttpMethod.POST))
                .andRespond(withSuccess(geminiResponse("summary"), MediaType.APPLICATION_JSON));

        String summary = service.generateSummary("chat content");

        assertEquals("summary", summary);
        server.verify();
    }

    @Test
    void generateSummaryShouldRetryTwiceAfter503AndThenSucceed() {
        RestTemplate restTemplate = new RestTemplate();
        GeminiApiProperties properties = defaultProperties();
        properties.setInitialDelayMillis(1);
        GeminiService service = newService(restTemplate, properties);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        server.expect(times(1), method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON));
        server.expect(times(1), method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON));
        server.expect(times(1), method(HttpMethod.POST))
                .andRespond(withSuccess(geminiResponse("summary"), MediaType.APPLICATION_JSON));

        String summary = service.generateSummary("chat content");

        assertEquals("summary", summary);
        server.verify();
    }

    @Test
    void generateSummaryShouldFailWithFriendlyMessageWhenAllAttemptsReturn503() {
        RestTemplate restTemplate = new RestTemplate();
        GeminiApiProperties properties = defaultProperties();
        properties.setInitialDelayMillis(1);
        properties.setMaxAttempts(3);
        GeminiService service = newService(restTemplate, properties);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        server.expect(times(3), method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON));

        GeminiIntegrationException exception = assertThrows(
                GeminiIntegrationException.class,
                () -> service.generateSummary("chat content"));

        assertEquals("O serviço de geração de resumo está temporariamente indisponível. Tente novamente em alguns instantes.",
                exception.getClientMessage());
        server.verify();
    }

    @Test
    void generateSummaryShouldNotRetryOnBadRequest() {
        RestTemplate restTemplate = new RestTemplate();
        GeminiApiProperties properties = defaultProperties();
        properties.setMaxAttempts(4);
        GeminiService service = newService(restTemplate, properties);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        server.expect(once(), method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON));

        GeminiIntegrationException exception = assertThrows(
                GeminiIntegrationException.class,
                () -> service.generateSummary("chat content"));

        assertEquals("Não foi possível concluir a geração de resumo no momento.", exception.getClientMessage());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getHttpStatus());
        server.verify();
    }

    @Test
    void generateSummaryShouldNotRetryOnUnauthorizedOrForbidden() {
        assertNoRetryForStatus(HttpStatus.UNAUTHORIZED);
        assertNoRetryForStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void generateSummaryShouldRetryOnTimeoutAndThenSucceed() {
        RestTemplate restTemplate = new RestTemplate();
        GeminiApiProperties properties = defaultProperties();
        properties.setInitialDelayMillis(1);
        GeminiService service = newService(restTemplate, properties);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        server.expect(times(1), method(HttpMethod.POST))
                .andRespond(request -> {
                    throw new ResourceAccessException("Read timed out", new SocketTimeoutException("Read timed out"));
                });
        server.expect(times(1), method(HttpMethod.POST))
                .andRespond(withSuccess(geminiResponse("summary"), MediaType.APPLICATION_JSON));

        String summary = service.generateSummary("chat content");

        assertEquals("summary", summary);
        server.verify();
    }

    @Test
    void generateSummaryShouldHonorRetryAfterButCapDelay() {
        RestTemplate restTemplate = new RestTemplate();
        GeminiApiProperties properties = defaultProperties();
        properties.setMaxRetryAfterMillis(5);
        GeminiService service = newService(restTemplate, properties);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        server.expect(times(1), method(HttpMethod.POST))
                .andRespond(request -> {
                    HttpHeaders headers = new HttpHeaders();
                    headers.put(HttpHeaders.RETRY_AFTER, List.of("999"));
                    throw HttpClientErrorException.create(
                            HttpStatus.TOO_MANY_REQUESTS,
                            "Too Many Requests",
                            headers,
                            new byte[0],
                            StandardCharsets.UTF_8);
                });
        server.expect(times(1), method(HttpMethod.POST))
                .andRespond(withSuccess(geminiResponse("summary"), MediaType.APPLICATION_JSON));

        String summary = service.generateSummary("chat content");

        assertEquals("summary", summary);
        server.verify();
    }

    @Test
    void propertiesShouldExposeSafeDefaults() {
        GeminiApiProperties properties = new GeminiApiProperties();

        assertEquals("gemini-2.5-flash-lite", properties.getModel());
        assertEquals(4, properties.getMaxAttempts());
        assertEquals(500L, properties.getInitialDelayMillis());
        assertEquals(2.0d, properties.getBackoffMultiplier());
        assertEquals(5000L, properties.getMaxRetryAfterMillis());
        assertEquals(2000, properties.getConnectTimeoutMillis());
        assertEquals(8000, properties.getReadTimeoutMillis());
    }

    private void assertNoRetryForStatus(HttpStatus status) {
        RestTemplate restTemplate = new RestTemplate();
        GeminiApiProperties properties = defaultProperties();
        properties.setMaxAttempts(4);
        GeminiService service = newService(restTemplate, properties);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        server.expect(once(), method(HttpMethod.POST))
                .andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON));

        assertThrows(GeminiIntegrationException.class, () -> service.generateSummary("chat content"));
        server.verify();
    }

    private GeminiService newService(RestTemplate restTemplate, GeminiApiProperties properties) {
        return new GeminiService(properties, restTemplate, mock(GoogleFileSearchService.class));
    }

    private GeminiApiProperties defaultProperties() {
        GeminiApiProperties properties = new GeminiApiProperties();
        properties.setKey("test-key");
        properties.setInitialDelayMillis(0);
        properties.setReadTimeoutMillis(1000);
        properties.setConnectTimeoutMillis(1000);
        properties.setMaxRetryAfterMillis(1000);
        return properties;
    }

    private String geminiResponse(String text) {
        return """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          { "text": "%s" }
                        ]
                      }
                    }
                  ]
                }
                """.formatted(text);
    }
}
