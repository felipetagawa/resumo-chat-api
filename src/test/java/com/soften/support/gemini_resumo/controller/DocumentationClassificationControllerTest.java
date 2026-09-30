package com.soften.support.gemini_resumo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soften.support.gemini_resumo.models.dtos.DocumentationCandidateDto;
import com.soften.support.gemini_resumo.models.dtos.DocumentationClassificationResponse;
import com.soften.support.gemini_resumo.models.dtos.DocumentationSuggestionDto;
import com.soften.support.gemini_resumo.service.ClassificationRateLimiter;
import com.soften.support.gemini_resumo.service.JevDocumentationClassificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentationClassificationController.class)
class DocumentationClassificationControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean JevDocumentationClassificationService service;
    @MockBean ClassificationRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        when(rateLimiter.tryAcquire()).thenReturn(true);
    }

    @Test
    void validRequestReturnsSuggestion() throws Exception {
        var candidates = List.of(new DocumentationCandidateDto("1339", "Rejeição 610"));
        var response = new DocumentationClassificationResponse(
                "single",
                List.of(new DocumentationSuggestionDto("1339", "Rejeição 610", 0.97d)),
                0.99d,
                0.01d,
                280L
        );

        when(service.classify("rejeição 610", candidates)).thenReturn(response);

        mockMvc.perform(post("/api/classification/documentation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "context", "rejeição 610",
                                "candidates", candidates
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("single"))
                .andExpect(jsonPath("$.suggestions[0].id").value("1339"));
    }
}
