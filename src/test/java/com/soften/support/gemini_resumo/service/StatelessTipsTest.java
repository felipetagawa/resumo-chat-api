package com.soften.support.gemini_resumo.service;

import com.soften.support.gemini_resumo.models.dtos.TipResponseDto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StatelessTipsTest {
    @Test
    void generatesTipsFromCurrentConversationWithoutHistory() {
        GeminiService gemini = mock(GeminiService.class);
        String summary = "**PROBLEMA / DÚVIDA:** O cliente não conseguiu emitir NF-e.\n"
                + "**SOLUÇÃO APRESENTADA:** Orientei conferir o certificado digital.\n"
                + "**OPORTUNIDADE DE UPSELL:** NÃO\n"
                + "**PRINTS DE ERRO OU DE MENSAGENS RELEVANTES:** Não\n"
                + "**HUMOR DO CLIENTE:** NEUTRO.\n"
                + "**MÓDULO:** NF-E (NOTA FISCAL ELETRÔNICA)";
        when(gemini.generateSummary("Conversa atual", null)).thenReturn(summary);
        when(gemini.ask(contains("Conversa atual")))
                .thenReturn("- Valide se a rejeição persiste após a conferência do certificado.\n- Verifique o retorno da SEFAZ.");

        CalledService service = new CalledService(new SummaryService(gemini), new SuggestionService(gemini));
        TipResponseDto response = service.processFullTip("Conversa atual", null);

        assertEquals("SUCESS", response.status());
        assertEquals(0, response.SimilarTagsFound());
        assertEquals(0, response.solutionsAnalyzed());
        assertEquals(2, response.tips().size());
        assertTrue(response.tips().get(0).contains("persiste"));
        assertEquals(summary, response.summary().fullSummary());
    }
}
