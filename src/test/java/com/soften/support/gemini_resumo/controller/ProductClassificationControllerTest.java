package com.soften.support.gemini_resumo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soften.support.gemini_resumo.config.TypeSafeApiProperties;
import com.soften.support.gemini_resumo.models.dtos.ProductClassificationResponse;
import com.soften.support.gemini_resumo.models.dtos.ProductSuggestionDto;
import com.soften.support.gemini_resumo.service.ClassificationRateLimiter;
import com.soften.support.gemini_resumo.service.JevIntegrationException;
import com.soften.support.gemini_resumo.service.JevProductClassificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductClassificationController.class)
class ProductClassificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JevProductClassificationService classificationService;

    @MockBean
    private ClassificationRateLimiter rateLimiter;

    @MockBean
    private TypeSafeApiProperties properties;

    @BeforeEach
    void setUp() {
        when(rateLimiter.tryAcquire()).thenReturn(true);
        when(properties.getSafeMaxConversationChars()).thenReturn(20_000);
    }

    @Test
    void blankConversationReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/classification/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("conversation", "   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").exists());

        verify(classificationService, never()).classify(anyString());
    }

    @Test
    void oversizedConversationReturnsPayloadTooLarge() throws Exception {
        mockMvc.perform(post("/api/classification/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "conversation",
                                "x".repeat(20_001)
                        ))))
                .andExpect(status().isPayloadTooLarge());

        verify(classificationService, never()).classify(anyString());
    }

    @Test
    void rateLimitReturnsTooManyRequests() throws Exception {
        when(rateLimiter.tryAcquire()).thenReturn(false);

        mockMvc.perform(post("/api/classification/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("conversation", "chat"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"));

        verify(classificationService, never()).classify(anyString());
    }

    @Test
    void validConversationReturnsSuggestions() throws Exception {
        var response = new ProductClassificationResponse(
                "multiple",
                List.of(
                        new ProductSuggestionDto("44", "NFS-E (NOTA FISCAL ELETRONICA DE SERVIÇO)", 0.51d),
                        new ProductSuggestionDto("1", "NF-E (NOTA FISCAL ELETRONICA)", 0.34d),
                        new ProductSuggestionDto("21", "NFC-E (NOTA FISCAL DO CONSUMIDOR ELETRONICA)", 0.11d)
                ),
                0.72d,
                0.02d,
                180L
        );

        when(classificationService.classify("chat")).thenReturn(response);

        mockMvc.perform(post("/api/classification/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("conversation", "chat"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("multiple"))
                .andExpect(jsonPath("$.suggestions[0].productId").value("44"))
                .andExpect(jsonPath("$.suggestions[1].productId").value("1"))
                .andExpect(jsonPath("$.latencyMs").value(180));
    }

    @Test
    void upstreamFailureReturnsControlledError() throws Exception {
        when(classificationService.classify("chat"))
                .thenThrow(new JevIntegrationException(
                        "Não foi possível identificar o produto agora. Tente novamente.",
                        HttpStatus.BAD_GATEWAY,
                        "upstream failure"
                ));

        mockMvc.perform(post("/api/classification/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("conversation", "chat"))))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.erro").value("Não foi possível identificar o produto agora. Tente novamente."));
    }
}
