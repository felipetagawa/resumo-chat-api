package com.soften.support.gemini_resumo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soften.support.gemini_resumo.config.GeminiApiProperties;
import com.soften.support.gemini_resumo.dto.SmartReplyRequest;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Offline export: every Gemini transport is intercepted before any network access. */
class SmartReplyBenchmarkExportTest {
    @Test
    void exportsRealRequestsForSyntheticCasesWithoutCallingGemini() throws Exception {
        Path root = Path.of("eval", "smart-reply");
        JSONArray cases = new JSONArray(Files.readString(root.resolve("cases.json")));
        assertEquals(30, cases.length());
        var ids = new HashSet<String>();
        JSONArray exported = new JSONArray();
        ObjectMapper mapper = new ObjectMapper();
        GeminiApiProperties properties = new GeminiApiProperties();
        properties.setKey("offline-placeholder");
        for (int i = 0; i < cases.length(); i++) {
            JSONObject scenario = cases.getJSONObject(i);
            assertTrue(ids.add(scenario.getString("id")), "Duplicate case");
            assertFalse(scenario.getString("review").isBlank());
            SmartReplyRequest request = mapper.readValue(scenario.getJSONObject("request").toString(), SmartReplyRequest.class);
            RestTemplate transport = new RestTemplate();
            GeminiService gemini = new GeminiService(properties, mock(RestTemplate.class),
                    mock(GoogleFileSearchService.class), transport);
            MockRestServiceServer server = MockRestServiceServer.bindTo(transport).build();
            server.expect(method(org.springframework.http.HttpMethod.POST)).andExpect(req -> {
                JSONObject body = new JSONObject(((MockClientHttpRequest) req).getBodyAsString());
                assertEquals(512, body.getJSONObject("generationConfig").getInt("maxOutputTokens"));
                assertEquals(0.3, body.getJSONObject("generationConfig").getDouble("temperature"));
                String prompt = body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text");
                assertFalse(prompt.contains("offline-placeholder"));
                String marker = "\n\nDADOS DO ATENDIMENTO (JSON):\n";
                JSONObject facts = new JSONObject(prompt.substring(prompt.indexOf(marker) + marker.length()));
                assertEquals(java.util.Set.of("conversation", "promptComplement"), facts.keySet());
                assertTrue(facts.getString("conversation").length() <= 16000);
                if (scenario.getString("id").equals("27-long-bounded")) {
                    assertTrue(facts.getString("conversation").contains("[trecho intermediário omitido]"));
                    assertTrue(facts.getString("conversation").endsWith("Cliente: Qual erro devo informar agora?"));
                }
                exported.put(new JSONObject().put("id", scenario.getString("id"))
                        .put("contextMode", scenario.getString("contextMode"))
                        .put("profile", request.profile().name()).put("body", body)
                        .put("checks", scenario.getJSONObject("checks"))
                        .put("review", scenario.getString("review")));
            }).andRespond(withSuccess("{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"Rascunho sintético de teste\"}]}}]}", MediaType.APPLICATION_JSON));
            assertEquals("Rascunho sintético de teste", new SmartReplyService(gemini).reply(request).reply());
            server.verify();
        }
        JSONObject bundle = new JSONObject().put("schemaVersion", 1)
                .put("baseUrl", properties.getGenerateContentBaseUrl())
                .put("defaultModel", properties.getModel())
                .put("transport", new JSONObject().put("connectTimeoutMs", 1500).put("readTimeoutMs", 4000)
                        .put("maxAttempts", Math.min(2, properties.getSafeMaxAttempts()))
                        .put("retryDelayMs", Math.min(250, properties.getSafeInitialDelayMillis()))
                        .put("extensionTimeoutMs", 15000))
                .put("cases", exported);
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(exported.toString().getBytes(StandardCharsets.UTF_8)));
        bundle.put("requestsSha256", digest);
        // Writes only when explicitly requested; never starts a Spring context or uses an API key.
        if (Boolean.getBoolean("smartReply.export")) {
            Path output = Path.of("target", "smart-reply-eval", "requests.json");
            Files.createDirectories(output.getParent());
            Files.writeString(output, bundle.toString(2), StandardCharsets.UTF_8);
        }
    }
}
