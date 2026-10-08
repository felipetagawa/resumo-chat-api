package com.soften.support.gemini_resumo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soften.support.gemini_resumo.service.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SmartReplyPrivacyTest {
    @ParameterizedTest
    @ValueSource(strings = {"privateNote", "privateNotes", "notes", "summaryObservation",
            "summaryObservations", "summaryComplement", "promptComplementSummary"})
    void privateAndSummaryOnlyFieldsAreRejectedBeforeProvider(String field) throws Exception {
        GeminiService gemini = mock(GeminiService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new SmartReplyController(
                new SmartReplyService(gemini), new SmartReplyRateLimiter(30))).build();
        String body = new ObjectMapper().writeValueAsString(Map.of("conversation", "Cliente: Qual erro devo informar?",
                "profile", "DIRECT", field, "PRIVATE_SYNTHETIC_MARKER"));
        mvc.perform(post("/api/gemini/responder").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(gemini);
    }
}
