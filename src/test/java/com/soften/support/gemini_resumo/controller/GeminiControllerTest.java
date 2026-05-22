package com.soften.support.gemini_resumo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soften.support.gemini_resumo.service.CalledService;
import com.soften.support.gemini_resumo.service.GeminiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GeminiController.class)
class GeminiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GeminiService geminiService;

    @MockBean
    private CalledService calledService;

    @Test
    void resumirJsonWithOnlyTextoShouldKeepOriginalFlow() throws Exception {
        when(geminiService.validateAndNormalizePromptComplement(isNull())).thenReturn(null);
        when(geminiService.generateSummary("chat content", null)).thenReturn("summary");

        mockMvc.perform(post("/api/gemini/resumir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("texto", "chat content"))))
                .andExpect(status().isOk());

        verify(geminiService).generateSummary("chat content", null);
    }

    @Test
    void resumirJsonWithPromptComplementShouldAppendComplementFlow() throws Exception {
        when(geminiService.validateAndNormalizePromptComplement("  extra user instruction  "))
                .thenReturn("extra user instruction");
        when(geminiService.generateSummary("chat content", "extra user instruction")).thenReturn("summary");

        mockMvc.perform(post("/api/gemini/resumir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "texto", "chat content",
                                "promptComplement", "  extra user instruction  "
                        ))))
                .andExpect(status().isOk());

        verify(geminiService).generateSummary("chat content", "extra user instruction");
    }

    @Test
    void resumirJsonWithBlankPromptComplementShouldIgnoreComplement() throws Exception {
        when(geminiService.validateAndNormalizePromptComplement("   ")).thenReturn(null);
        when(geminiService.generateSummary("chat content", null)).thenReturn("summary");

        mockMvc.perform(post("/api/gemini/resumir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "texto", "chat content",
                                "promptComplement", "   "
                        ))))
                .andExpect(status().isOk());

        verify(geminiService).generateSummary("chat content", null);
    }

    @Test
    void resumirJsonWithOversizedPromptComplementShouldReturnBadRequest() throws Exception {
        when(geminiService.validateAndNormalizePromptComplement(anyString()))
                .thenThrow(new IllegalArgumentException("Campo 'promptComplement' excede o limite de 2000 caracteres."));

        mockMvc.perform(post("/api/gemini/resumir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "texto", "chat content",
                                "promptComplement", "x".repeat(2001)
                        ))))
                .andExpect(status().isBadRequest());

        verify(geminiService, never()).generateSummary(anyString(), anyString());
    }
}
