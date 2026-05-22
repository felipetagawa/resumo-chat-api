package com.soften.support.gemini_resumo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soften.support.gemini_resumo.models.dtos.SummaryDto;
import com.soften.support.gemini_resumo.models.dtos.TipResponseDto;
import com.soften.support.gemini_resumo.service.CalledService;
import com.soften.support.gemini_resumo.service.GeminiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CalledController.class)
class CalledControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CalledService calledService;

    @MockBean
    private GeminiService geminiService;

    @Test
    void processTipShouldAcceptOptionalPromptComplement() throws Exception {
        TipResponseDto response = TipResponseDto.builder()
                .summary((SummaryDto) null)
                .tips(List.of("tip"))
                .status("SUCESS")
                .build();

        when(geminiService.validateAndNormalizePromptComplement("  extra context  ")).thenReturn("extra context");
        when(calledService.processFullTip("chat content", "extra context")).thenReturn(response);

        mockMvc.perform(post("/api/chamado/processar-dica")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "texto", "chat content",
                                "promptComplement", "  extra context  "
                        ))))
                .andExpect(status().isOk());

        verify(calledService).processFullTip("chat content", "extra context");
    }

    @Test
    void processTipWithOversizedPromptComplementShouldReturnBadRequest() throws Exception {
        when(geminiService.validateAndNormalizePromptComplement(anyString()))
                .thenThrow(new IllegalArgumentException("Campo 'promptComplement' excede o limite de 2000 caracteres."));

        mockMvc.perform(post("/api/chamado/processar-dica")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "texto", "chat content",
                                "promptComplement", "x".repeat(2001)
                        ))))
                .andExpect(status().isBadRequest());
    }
}
