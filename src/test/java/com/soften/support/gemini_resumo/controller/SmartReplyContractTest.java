package com.soften.support.gemini_resumo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soften.support.gemini_resumo.service.*;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SmartReplyContractTest {
    final ObjectMapper json = new ObjectMapper();
    GeminiService gemini;
    MockMvc mvc;
    @BeforeEach void setup() {
        gemini = mock(GeminiService.class);
        when(gemini.generateInteractive(anyString(), anyString(), anyBoolean())).thenReturn("Pode me mandar o XML?");
        mvc = MockMvcBuilders.standaloneSetup(new SmartReplyController(new SmartReplyService(gemini), new SmartReplyRateLimiter(30))).build();
    }
    ResultActions send(Object body) throws Exception {
        return mvc.perform(post("/api/gemini/responder").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }
    @Test void legacyProfileFieldsAreAcceptedAndIgnored() throws Exception {
        send(Map.of("conversation", "Cliente: oi", "profile", "EMPATHETIC")).andExpect(status().isOk());
        send(Map.of("conversation", "Cliente: oi", "profile", "CUSTOM", "styleInstruction", "LEGACY_STYLE")).andExpect(status().isOk());
        var policy = ArgumentCaptor.forClass(String.class);
        var data = ArgumentCaptor.forClass(String.class);
        verify(gemini, times(2)).generateInteractive(policy.capture(), data.capture(), anyBoolean());
        for (String p : policy.getAllValues()) assertFalse(p.contains("LEGACY_STYLE"));
        for (String d : data.getAllValues()) assertFalse(d.contains("LEGACY_STYLE"));
    }
    @Test void instructionAndSamplesTravelOnlyAsJsonData() throws Exception {
        send(Map.of("conversation", "Cliente: oi", "replyInstruction", "peça o XML", "styleSamples", List.of("Opa, beleza?"), "regenerate", true))
                .andExpect(status().isOk());
        var data = ArgumentCaptor.forClass(String.class);
        verify(gemini).generateInteractive(anyString(), data.capture(), eq(true));
        JSONObject facts = new JSONObject(data.getValue());
        assertEquals("peça o XML", facts.getString("replyInstruction"));
        assertEquals("Opa, beleza?", facts.getJSONArray("styleSamples").getString(0));
    }
    @Test void invalidInstructionOrSamplesAreRejectedBeforeProvider() throws Exception {
        List<Map<String, Object>> bodies = List.of(
                Map.of("conversation", "oi", "replyInstruction", "x".repeat(601)),
                Map.of("conversation", "oi", "replyInstruction", 3),
                Map.of("conversation", "oi", "styleSamples", "texto solto"),
                Map.of("conversation", "oi", "styleSamples", Collections.nCopies(9, "amostra válida aqui")),
                Map.of("conversation", "oi", "styleSamples", List.of("x".repeat(301))),
                Map.of("conversation", "oi", "styleSamples", List.of(" ")),
                Map.of("conversation", "oi", "styleSamples", List.of(12)),
                Map.of("conversation", "oi", "profile", 0));
        for (var body : bodies) send(body).andExpect(status().isBadRequest());
        verifyNoInteractions(gemini);
    }
    @Test void exactLimitsAreAccepted() throws Exception {
        send(Map.of("conversation", "oi", "replyInstruction", "x".repeat(600), "styleSamples", Collections.nCopies(8, "y".repeat(300))))
                .andExpect(status().isOk());
    }
    @Test void clicheOnlyReplyAsksForAnotherWording() throws Exception {
        when(gemini.generateInteractive(anyString(), anyString(), anyBoolean())).thenReturn("Fico à disposição!");
        send(Map.of("conversation", "oi")).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.erro").value("Não saiu uma boa sugestão. Tente ↻ Outra."));
    }
}
