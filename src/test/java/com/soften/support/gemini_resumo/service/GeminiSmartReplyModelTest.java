package com.soften.support.gemini_resumo.service;

import com.soften.support.gemini_resumo.config.GeminiApiProperties;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiSmartReplyModelTest {
    private GeminiApiProperties configured(String model, boolean enabled) {
        var p = new GeminiApiProperties();
        p.setKey("dummy");
        new Binder(new MapConfigurationPropertySource(Map.of(
            "gemini.api.smart-reply-model", model,
            "gemini.api.smart-reply-premium-enabled", String.valueOf(enabled))))
            .bind("gemini.api", Bindable.ofInstance(p));
        return p;
    }
    @Test void premiumHasIsolatedModelAndLowThinkingWithoutSampling() {
        var p = configured("gemini-3.8-flash", true);
        var interactive = new RestTemplate();
        var regular = new RestTemplate();
        var server = MockRestServiceServer.bindTo(interactive).build();
        var globalServer = MockRestServiceServer.bindTo(regular).build();
        var service = new GeminiService(p, regular, mock(GoogleFileSearchService.class), interactive);
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1/models/gemini-3.8-flash:generateContent?key=dummy"))
            .andExpect(req -> {
                var config = new JSONObject(((MockClientHttpRequest)req).getBodyAsString()).getJSONObject("generationConfig");
                assertEquals(512, config.getInt("maxOutputTokens"));
                assertEquals("LOW", config.getJSONObject("thinkingConfig").getString("thinkingLevel"));
                assertFalse(config.has("temperature"));
                assertFalse(config.has("topP"));
            }).andRespond(withSuccess(response(), MediaType.APPLICATION_JSON));
        assertEquals("Hello world", service.generateInteractive("policy", "{}"));
        globalServer.expect(requestTo("https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash-lite:generateContent?key=dummy"))
            .andExpect(req -> {
                var config = new JSONObject(((MockClientHttpRequest)req).getBodyAsString()).getJSONObject("generationConfig");
                assertEquals(0.3, config.getDouble("temperature"));
                assertEquals(2048, config.getInt("maxOutputTokens"));
                assertFalse(config.has("thinkingConfig"));
            }).andRespond(withSuccess("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"summary\"}]}}]}", MediaType.APPLICATION_JSON));
        assertEquals("summary", service.generateSummary("synthetic"));
        server.verify(); globalServer.verify();
    }
    @Test void disabledPremiumUsesGlobalModelAndOriginalConfig() {
        var transport = new RestTemplate();
        var server = MockRestServiceServer.bindTo(transport).build();
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash-lite:generateContent?key=dummy"))
            .andExpect(req -> assertEquals(0.3, new JSONObject(((MockClientHttpRequest)req).getBodyAsString())
                .getJSONObject("generationConfig").getDouble("temperature")))
            .andRespond(withSuccess(response(), MediaType.APPLICATION_JSON));
        assertEquals("Hello world", new GeminiService(configured("gemini-3.8-flash", false), transport,
            mock(GoogleFileSearchService.class)).generateInteractive("policy", "{}"));
        server.verify();
    }
    @Test void invalidModelFailsBeforeNetworkEvenWhenDisabled() {
        var transport = mock(RestTemplate.class);
        assertThrows(IllegalStateException.class, () -> new GeminiService(configured("arbitrary-model", false),
            transport, mock(GoogleFileSearchService.class)).generateInteractive("policy", "{}"));
        org.mockito.Mockito.verifyNoInteractions(transport);
    }
    @Test void incompleteAndBlockedResponsesAreRejectedWithoutRetry() {
        for (String reason : new String[]{"MAX_TOKENS", "SAFETY", "RECITATION"}) {
            var transport = new RestTemplate();
            var server = MockRestServiceServer.bindTo(transport).build();
            server.expect(requestTo(org.hamcrest.Matchers.containsString("gemini-2.5-flash-lite")))
                .andRespond(withSuccess(response().replace("STOP", reason), MediaType.APPLICATION_JSON));
            assertThrows(GeminiIntegrationException.class, () -> new GeminiService(configured("", false),
                transport, mock(GoogleFileSearchService.class)).generateInteractive("policy", "{}"));
            server.verify();
        }
    }

    @Test void timeoutConfigurationIsBoundedAndDefaultsAreCompatible() {
        var p = new GeminiApiProperties();
        assertEquals(4000, p.getSafeSmartReplyReadTimeoutMillis());
        p.setSmartReplyReadTimeoutMillis(Integer.MAX_VALUE);
        assertEquals(5000, p.getSafeSmartReplyReadTimeoutMillis());
        p.setSmartReplyReadTimeoutMillis(0);
        assertEquals(1000, p.getSafeSmartReplyReadTimeoutMillis());
        assertEquals(p.getModel(), p.resolveSmartReplyModel());
    }
    @Test void premiumNeverExceedsTwoAttemptsAndLogsOnlyMetadata() {
        var p = configured("gemini-3.8-flash", true);
        p.setMaxAttempts(99); p.setInitialDelayMillis(0);
        var transport = new RestTemplate();
        var server = MockRestServiceServer.bindTo(transport).build();
        server.expect(org.springframework.test.web.client.ExpectedCount.times(2),
                requestTo(org.hamcrest.Matchers.containsString("gemini-3.8-flash")))
            .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE).body("PRIVATE_PROVIDER_BODY"));
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("gemini.usage");
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start(); logger.addAppender(appender);
        try {
            assertThrows(GeminiIntegrationException.class, () -> new GeminiService(p, transport,
                mock(GoogleFileSearchService.class)).generateInteractive("PRIVATE_POLICY", "PRIVATE_DATA"));
            assertEquals(2, appender.list.size());
            for (var entry : appender.list) {
                String line = entry.getFormattedMessage();
                assertFalse(line.contains("PRIVATE")); assertFalse(line.contains("dummy"));
                var event = new JSONObject(line);
                assertEquals("error", event.getString("outcome"));
                assertTrue(event.isNull("estimatedUsd"));
            }
        } finally { logger.detachAppender(appender); appender.stop(); }
        server.verify();
    }
    private String response() {
        return "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"thought\":true,\"text\":\"private reasoning\"},{\"text\":\"Hello \"},{\"text\":\"world\"}]}}]}";
    }
}
