package com.soften.support.gemini_resumo.service;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
class GeminiUsageTest {
    private final LocalDate promo = LocalDate.of(2026, 12, 31);
    private String body(String extra) {
        return "{\"personal\":\"PRIVATE_TRANSCRIPT\",\"usageMetadata\":{\"promptTokenCount\":1000,\"candidatesTokenCount\":100,"+extra+"}}";
    }
    @Test void billsThinkingOnceAndSeparatesPeriodsWithoutPayload() {
        var event = GeminiUsage.metadata("gemini-3.8-flash", "smart_reply",
            body("\"thoughtsTokenCount\":200,\"totalTokenCount\":1300"), 42, "success", 1, promo);
        assertEquals(0.001875, event.getDouble("estimatedUsd2026"), 1e-12);
        assertEquals(0.003750, event.getDouble("estimatedUsd2027"), 1e-12);
        assertEquals(0.001875, event.getDouble("estimatedUsd"), 1e-12);
        assertEquals(200, event.getLong("thinkingTokens"));
        assertFalse(event.toString().contains("PRIVATE"));
        assertEquals("smart_reply", event.getString("feature"));
    }
    @Test void switchesPricesAtUtcYearBoundaryAndAccountsForCache() {
        var event = GeminiUsage.metadata("gemini-3.8-flash", "smart_reply",
            body("\"cachedContentTokenCount\":200,\"totalTokenCount\":1100"), 1, "success", 1,
            LocalDate.of(2027, 1, 1));
        assertEquals(0, event.getLong("thinkingTokens"));
        assertEquals(0.001980, event.getDouble("estimatedUsd"), 1e-12);
    }
    @Test void missingInvalidAndInconsistentMetadataRemainUnknown() {
        for (String b : new String[]{null, "invalid", "{}",
                body("\"thoughtsTokenCount\":200,\"totalTokenCount\":1200"),
                body("\"thoughtsTokenCount\":-1,\"totalTokenCount\":1100"),
                body("\"totalTokenCount\":1100,\"cachedContentTokenCount\":1001")}) {
            var event = GeminiUsage.metadata("gemini-3.8-flash", "smart_reply", b, 1, "timeout", 2, promo);
            assertTrue(event.isNull("estimatedUsd"));
            assertFalse(event.getBoolean("usageComplete"));
            assertEquals("timeout", event.getString("outcome"));
        }
    }
    @Test void unknownModelsHaveUnknownCostsEvenWithValidUsage() {
        var event = GeminiUsage.metadata("another-model", "report",
            body("\"totalTokenCount\":1100"), 1, "success", 1, promo);
        assertTrue(event.isNull("estimatedUsd"));
    }
}
