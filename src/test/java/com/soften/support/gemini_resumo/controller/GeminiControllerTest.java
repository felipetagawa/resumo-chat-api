package com.soften.support.gemini_resumo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soften.support.gemini_resumo.service.CalledService;
import com.soften.support.gemini_resumo.service.GeminiService;
import com.soften.support.gemini_resumo.service.GeminiIntegrationException;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
        when(geminiService.validateAndNormalizePromptComplement("extra user instruction"))
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
        when(geminiService.validateAndNormalizePromptComplement(isNull())).thenReturn(null);
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

    @Test
    void resumirJsonShouldReturnFriendlyStableErrorWhenGeminiIsUnavailable() throws Exception {
        when(geminiService.validateAndNormalizePromptComplement(isNull())).thenReturn(null);
        when(geminiService.generateSummary("chat content", null))
                .thenThrow(new GeminiIntegrationException(
                        "O serviço de geração de resumo está temporariamente indisponível. Tente novamente em alguns instantes.",
                        org.springframework.http.HttpStatus.BAD_GATEWAY,
                        "https://generativelanguage.googleapis.com/...key=secret",
                        new RuntimeException("503")));

        mockMvc.perform(post("/api/gemini/resumir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("texto", "chat content"))))
                .andExpect(status().isBadGateway())
                .andExpect(content().json("""
                        {
                          "erro": "O serviço de geração de resumo está temporariamente indisponível. Tente novamente em alguns instantes."
                        }
                        """));
    }

    @Test
    void resumirJsonShouldNotExposeInternalGeminiDetails() throws Exception {
        when(geminiService.validateAndNormalizePromptComplement(isNull())).thenReturn(null);
        when(geminiService.generateSummary("chat content", null))
                .thenThrow(new GeminiIntegrationException(
                        "O serviço de geração de resumo está temporariamente indisponível. Tente novamente em alguns instantes.",
                        org.springframework.http.HttpStatus.BAD_GATEWAY,
                        "POST https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash-lite:generateContent?key=secret",
                        new RuntimeException("{\"error\":{\"code\":503}}")));

        mockMvc.perform(post("/api/gemini/resumir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("texto", "chat content"))))
                .andExpect(status().isBadGateway())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("generativelanguage.googleapis.com"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("503"))));
    }

    @Test
    void resumirJsonShouldReturnInternalServerErrorForPermanentGeminiFailure() throws Exception {
        when(geminiService.validateAndNormalizePromptComplement(isNull())).thenReturn(null);
        when(geminiService.generateSummary("chat content", null))
                .thenThrow(new GeminiIntegrationException(
                        "Não foi possível concluir a geração de resumo no momento.",
                        org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                        "Falha nao transitoria retornada pelo Gemini. status=401",
                        new RuntimeException("401")));

        mockMvc.perform(post("/api/gemini/resumir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("texto", "chat content"))))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json("""
                        {
                          "erro": "Não foi possível concluir a geração de resumo no momento."
                        }
                        """));
    }
}
