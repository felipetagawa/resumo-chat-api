package com.soften.support.gemini_resumo.service;
import com.soften.support.gemini_resumo.config.GeminiApiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.MediaType;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
class GeminiFileSearchUsageTest {
    @Test void docsKeepGlobalModelAndReturnSameTextWithOnlyAggregateUsageLogged() {
        var p = new GeminiApiProperties(); p.setKey("dummy");
        p.setSmartReplyModel("gemini-3.8-flash"); p.setSmartReplyPremiumEnabled(true);
        var service = new GoogleFileSearchService(p);
        ReflectionTestUtils.setField(service, "manualsStoreId", "stores/synthetic");
        var transport = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        var server = MockRestServiceServer.bindTo(transport).build();
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=dummy"))
            .andRespond(withSuccess("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"synthetic manual\"}]}}],"
                + "\"usageMetadata\":{\"promptTokenCount\":1000,\"candidatesTokenCount\":100,\"totalTokenCount\":1100}}",
                MediaType.APPLICATION_JSON));
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("gemini.usage");
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start(); logger.addAppender(appender);
        try {
            assertEquals("synthetic manual", service.searchManuals("synthetic question", "synthetic policy"));
            assertEquals(1, appender.list.size());
            var event = new org.json.JSONObject(appender.list.getFirst().getFormattedMessage());
            assertEquals("docs", event.getString("feature"));
            assertEquals("gemini-2.5-flash-lite", event.getString("model"));
            assertEquals(0.00014, event.getDouble("estimatedUsd2026"), 1e-12);
            assertFalse(event.toString().contains("synthetic"));
        } finally { logger.detachAppender(appender); appender.stop(); }
        server.verify();
    }
}
