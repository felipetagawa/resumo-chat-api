package com.soften.support.gemini_resumo.service;

import com.soften.support.gemini_resumo.config.GeminiApiProperties;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiProblemLineModelTest {
    @Test void problemLineIsPinnedToFlashLiteWithThinkingOffEvenWhenGlobalModelThinks() {
        var p = new GeminiApiProperties();
        p.setKey("dummy");
        p.setModel("gemini-2.5-flash");
        var interactive = new RestTemplate();
        var server = MockRestServiceServer.bindTo(interactive).build();
        var service = new GeminiService(p, new RestTemplate(), mock(GoogleFileSearchService.class), interactive);
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash-lite:generateContent?key=dummy"))
            .andExpect(req -> {
                var config = new JSONObject(((MockClientHttpRequest) req).getBodyAsString()).getJSONObject("generationConfig");
                assertEquals(64, config.getInt("maxOutputTokens"));
                // v1 answers 400 "Thinking is not enabled for api version v1" to any thinkingConfig.
                assertFalse(config.has("thinkingConfig"));
            }).andRespond(withSuccess("{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"Erro no XML\"}]}}]}",
                MediaType.APPLICATION_JSON));
        assertEquals("Erro no XML", service.generateProblemLine("erro no xml"));
        server.verify();
    }
    @Test void flash38GetsLowThinkingAndRoomForIt() {
        var p = new GeminiApiProperties();
        p.setKey("dummy");
        p.setProblemLineModel("gemini-3.8-flash");
        var interactive = new RestTemplate();
        var server = MockRestServiceServer.bindTo(interactive).build();
        var service = new GeminiService(p, new RestTemplate(), mock(GoogleFileSearchService.class), interactive);
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1/models/gemini-3.8-flash:generateContent?key=dummy"))
            .andExpect(req -> {
                var config = new JSONObject(((MockClientHttpRequest) req).getBodyAsString()).getJSONObject("generationConfig");
                assertEquals(512, config.getInt("maxOutputTokens"));
                assertEquals("LOW", config.getJSONObject("thinkingConfig").getString("thinkingLevel"));
                assertFalse(config.has("temperature"));
            }).andRespond(withSuccess("{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"NF-e rejeição 539\"}]}}]}",
                MediaType.APPLICATION_JSON));
        assertEquals("NF-e rejeição 539", service.generateProblemLine("rejeição 539"));
        server.verify();
    }
    @Test void v1betaStillSwitchesThinkingOff() {
        var p = new GeminiApiProperties();
        p.setKey("dummy");
        p.setGenerateContentBaseUrl("https://generativelanguage.googleapis.com/v1beta/models");
        var interactive = new RestTemplate();
        var server = MockRestServiceServer.bindTo(interactive).build();
        var service = new GeminiService(p, new RestTemplate(), mock(GoogleFileSearchService.class), interactive);
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=dummy"))
            .andExpect(req -> {
                var config = new JSONObject(((MockClientHttpRequest) req).getBodyAsString()).getJSONObject("generationConfig");
                assertEquals(0, config.getJSONObject("thinkingConfig").getInt("thinkingBudget"));
            }).andRespond(withSuccess("{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"Erro no XML\"}]}}]}",
                MediaType.APPLICATION_JSON));
        assertEquals("Erro no XML", service.generateProblemLine("erro no xml"));
        server.verify();
    }
}
