package com.soften.support.gemini_resumo.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class GeminiServiceTest {

    @Test
    void createSummaryPromptWithComplementShouldKeepBasePromptWhenComplementMissing() {
        GeminiService service = new GeminiService(mock(GoogleFileSearchService.class));

        String prompt = service.createSummaryPromptWithComplement(null);

        assertNotNull(prompt);
        assertFalse(prompt.contains("INSTRUCOES COMPLEMENTARES DO USUARIO"));
    }

    @Test
    void createSummaryPromptWithComplementShouldAppendComplementSection() {
        GeminiService service = new GeminiService(mock(GoogleFileSearchService.class));

        String prompt = service.createSummaryPromptWithComplement("  foco em concisao  ");

        assertTrue(prompt.contains("INSTRUCOES COMPLEMENTARES DO USUARIO"));
        assertTrue(prompt.contains("foco em concisao"));
    }

    @Test
    void validateAndNormalizePromptComplementShouldIgnoreBlank() {
        GeminiService service = new GeminiService(mock(GoogleFileSearchService.class));

        String normalized = service.validateAndNormalizePromptComplement("   ");

        assertNull(normalized);
    }

    @Test
    void validateAndNormalizePromptComplementShouldRejectOversized() {
        GeminiService service = new GeminiService(mock(GoogleFileSearchService.class));

        assertThrows(IllegalArgumentException.class,
                () -> service.validateAndNormalizePromptComplement("x".repeat(GeminiService.PROMPT_COMPLEMENT_MAX_LENGTH + 1)));
    }
}
