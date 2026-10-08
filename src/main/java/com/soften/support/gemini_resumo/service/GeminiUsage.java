package com.soften.support.gemini_resumo.service;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;

/** Only aggregate metadata enters these records; never log a body, URI or exception. */
final class GeminiUsage {
    private static final Logger LOG = LoggerFactory.getLogger("gemini.usage");
    private static final Set<String> FEATURES = Set.of("smart_reply", "report", "ask", "docs", "classification", "file_search");
    private GeminiUsage() {}

    static JSONObject metadata(String model, String feature, String body, long durationMs,
            String outcome, int attempt, LocalDate date) {
        JSONObject usage = new JSONObject();
        try { usage = new JSONObject(body).optJSONObject("usageMetadata"); }
        catch (Exception ignored) { /* Missing usage is unknown, never free. */ }
        if (usage == null) usage = new JSONObject();
        Long input = count(usage, "promptTokenCount");
        Long output = count(usage, "candidatesTokenCount");
        Long thoughts = count(usage, "thoughtsTokenCount");
        Long total = count(usage, "totalTokenCount");
        Long cached = count(usage, "cachedContentTokenCount");
        Long tools = count(usage, "toolUsePromptTokenCount");
        if (cached == null && !usage.has("cachedContentTokenCount")) cached = 0L;
        if (tools == null && !usage.has("toolUsePromptTokenCount")) tools = 0L;
        // Absent protobuf zero fields may be inferred only from a consistent total.
        if (thoughts == null && !usage.has("thoughtsTokenCount")
                && input != null && output != null && total != null && tools != null
                && total >= input + output + tools) thoughts = total - input - output - tools;
        boolean complete = input != null && output != null && thoughts != null && total != null
                && cached != null && tools != null && cached <= input
                && total == input + output + thoughts + tools;
        Double promo = complete ? cost(model, input, output, thoughts, cached, false) : null;
        Double standard = complete ? cost(model, input, output, thoughts, cached, true) : null;
        boolean future = !date.isBefore(LocalDate.of(2027, 1, 1));
        return new JSONObject().put("event", "gemini_usage")
                .put("model", model != null && model.matches("[a-zA-Z0-9._-]{1,100}") ? model : "unknown")
                .put("feature", FEATURES.contains(feature) ? feature : "unknown")
                .put("outcome", Set.of("success", "timeout", "error").contains(outcome) ? outcome : "error")
                .put("attempt", attempt).put("durationMs", Math.max(0, durationMs))
                .put("inputTokens", nullable(input)).put("outputTokens", nullable(output))
                .put("thinkingTokens", nullable(thoughts)).put("cachedTokens", nullable(cached))
                .put("totalTokens", nullable(total)).put("usageComplete", complete)
                .put("estimatedUsd", nullable(future ? standard : promo))
                .put("estimatedUsd2026", nullable(promo)).put("estimatedUsd2027", nullable(standard))
                .put("pricingPeriod", future ? "standard_2027" : "promo_2026")
                .put("pricingCheckedAt", "2026-10-08")
                .put("costScope", "standard_text_tokens_only");
    }

    static void log(String model, String feature, String body, long durationMs, String outcome, int attempt) {
        // Telemetry must not change response behavior or introduce another retry.
        try { LOG.info("{}", metadata(model, feature, body, durationMs, outcome, attempt, LocalDate.now(ZoneOffset.UTC))); }
        catch (Exception ignored) { LOG.warn("gemini_usage_metadata_unavailable"); }
    }
    private static Object nullable(Object value) { return value == null ? JSONObject.NULL : value; }
    private static Long count(JSONObject usage, String field) {
        Object value = usage.opt(field);
        if (!(value instanceof Number number)) return null;
        double raw = number.doubleValue();
        return Double.isFinite(raw) && raw >= 0 && raw <= 100_000_000 && raw == Math.rint(raw)
                ? number.longValue() : null;
    }
    private static Double cost(String model, long input, long output, long thoughts, long cached, boolean future) {
        double inputRate, outputRate, cacheRate;
        if ("gemini-2.5-flash-lite".equals(model)) {
            inputRate = 0.10; outputRate = 0.40; cacheRate = 0.01;
        } else if ("gemini-3.8-flash".equals(model)) {
            inputRate = future ? 1.50 : 0.75;
            outputRate = future ? 7.50 : 3.75;
            cacheRate = future ? 0.15 : 0.075;
        } else return null;
        return ((input - cached) * inputRate + cached * cacheRate + (output + thoughts) * outputRate) / 1_000_000;
    }
}
