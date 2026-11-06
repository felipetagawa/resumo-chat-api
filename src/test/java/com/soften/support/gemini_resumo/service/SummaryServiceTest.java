package com.soften.support.gemini_resumo.service;

import com.soften.support.gemini_resumo.docs.ClaudeService;
import com.soften.support.gemini_resumo.models.enums.ModulesCalled;
import com.soften.support.gemini_resumo.models.enums.MoodClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SummaryServiceTest {

    static final String FULL_SUMMARY = """
            **PROBLEMA / DÚVIDA:** O cliente tentou emitir a NF-e e recebeu a rejeição 539, impedindo a venda.

            **SOLUÇÃO APRESENTADA:** Consultei a chave na SEFAZ e reenviei a número doc: 1520.

            **OPORTUNIDADE DE UPSELL:** NÃO. Não havia contexto para oferta.

            **PRINTS DE ERRO OU DE MENSAGENS RELEVANTES:** Sim

            **HUMOR DO CLIENTE:** NEUTRO.

            **MÓDULO:** NF-E (NOTA FISCAL ELETRÔNICA)
            """;

    @SuppressWarnings("unchecked")
    @Test void claudeWritesTheSummaryWithTheSamePromptAndGeminiIsNotCalled() {
        ClaudeService claude = mock(ClaudeService.class);
        when(claude.generateOnce(anyString(), anyList(), anyLong(), any(Duration.class), eq("summary")))
                .thenReturn(Optional.of(FULL_SUMMARY));
        GeminiService gemini = mock(GeminiService.class);

        assertEquals(FULL_SUMMARY, new SummaryService(gemini, claude).summarize("Cliente: rejeição 539", "liguei depois"));

        ArgumentCaptor<List<ClaudeService.Turn>> turns = ArgumentCaptor.forClass(List.class);
        verify(claude).generateOnce(eq(GeminiService.createSummaryPrompt()), turns.capture(),
                eq(SummaryService.CLAUDE_MAX_TOKENS), eq(SummaryService.CLAUDE_TIMEOUT), eq("summary"));
        assertEquals(1, turns.getValue().size());
        assertTrue(turns.getValue().get(0).fromUser());
        assertEquals(GeminiService.buildSummaryInput("Cliente: rejeição 539", "liguei depois"),
                turns.getValue().get(0).text());
        verifyNoInteractions(gemini);
    }

    @Test void geminiSummarizesWhenClaudeIsNotConfigured() {
        GeminiService gemini = mock(GeminiService.class);
        when(gemini.generateSummary("chat", null)).thenReturn("resumo gemini");

        assertEquals("resumo gemini", new SummaryService(gemini).summarize("chat", null));
        assertEquals("resumo gemini", new SummaryService(gemini, null).summarize("chat", null));
    }

    @Test void geminiSummarizesWhenClaudeFailsOrTimesOut() {
        ClaudeService claude = mock(ClaudeService.class);
        when(claude.generateOnce(anyString(), anyList(), anyLong(), any(Duration.class), anyString()))
                .thenReturn(Optional.empty());
        GeminiService gemini = mock(GeminiService.class);
        when(gemini.generateSummary("chat", "extra")).thenReturn("resumo gemini");

        assertEquals("resumo gemini", new SummaryService(gemini, claude).summarize("chat", "extra"));
        verify(gemini).generateSummary("chat", "extra");
    }

    @Test void geminiSummarizesWhenClaudeLeavesOutASection() {
        // A reply cut at max_tokens, or one that drifted from the format the extension parses.
        String truncated = FULL_SUMMARY.substring(0, FULL_SUMMARY.indexOf("**MÓDULO:**"));
        ClaudeService claude = mock(ClaudeService.class);
        when(claude.generateOnce(anyString(), anyList(), anyLong(), any(Duration.class), anyString()))
                .thenReturn(Optional.of(truncated));
        GeminiService gemini = mock(GeminiService.class);
        when(gemini.generateSummary("chat", null)).thenReturn("resumo gemini");

        assertEquals("resumo gemini", new SummaryService(gemini, claude).summarize("chat", null));
    }

    @Test void geminiSummarizesWhenClaudeDropsTheBoldLabelsTheServerParses() {
        ClaudeService claude = mock(ClaudeService.class);
        when(claude.generateOnce(anyString(), anyList(), anyLong(), any(Duration.class), anyString()))
                .thenReturn(Optional.of(FULL_SUMMARY.replace("**", "")));
        GeminiService gemini = mock(GeminiService.class);
        when(gemini.generateSummary("chat", null)).thenReturn("resumo gemini");

        assertEquals("resumo gemini", new SummaryService(gemini, claude).summarize("chat", null));
    }

    @Test void geminiErrorsStillReachTheCallerWhenClaudeCannotAnswer() {
        GeminiService gemini = mock(GeminiService.class);
        GeminiIntegrationException unavailable = new GeminiIntegrationException(
                "indisponível", org.springframework.http.HttpStatus.BAD_GATEWAY, (Throwable) null);
        when(gemini.generateSummary("chat", null)).thenThrow(unavailable);

        assertSame(unavailable, assertThrows(GeminiIntegrationException.class,
                () -> new SummaryService(gemini).summarize("chat", null)));
    }

    @Test void boldInsideAValueDoesNotCutTheField() {
        String summary = """
                **PROBLEMA / DÚVIDA:** O cliente tentou emitir a **NF-e 1520** e recebeu a rejeição 539.

                **SOLUÇÃO APRESENTADA:** Consultei a chave na **SEFAZ** e reenviei a número doc: 1520.

                **OPORTUNIDADE DE UPSELL:** **NÃO.** Não havia contexto para oferta.

                **PRINTS DE ERRO OU DE MENSAGENS RELEVANTES:** **Sim**

                **HUMOR DO CLIENTE:** **IRRITADO.**

                **MÓDULO:** **NFC-E (NOTA FISCAL DO CONSUMIDOR ELETRÔNICA)**
                """;

        var fields = new SummaryService(mock(GeminiService.class)).extractFieldsFromSummary(summary);

        assertEquals("O cliente tentou emitir a NF-e 1520 e recebeu a rejeição 539.", fields.problem());
        assertEquals("Consultei a chave na SEFAZ e reenviei a número doc: 1520.", fields.solution());
        assertEquals("NÃO. Não havia contexto para oferta.", fields.upsell());
        assertTrue(fields.prints());
        assertEquals(MoodClient.IRRITATED, fields.mood());
        assertEquals(ModulesCalled.NFCE, fields.modules());
    }

    @Test void aNoteAfterTheModuleDoesNotChangeTheModule() {
        String summary = FULL_SUMMARY.replace("**MÓDULO:** NF-E (NOTA FISCAL ELETRÔNICA)",
                "**MÓDULO:** GENÉRICO\n\n**Observação:** o certificado do cliente vence mês que vem.");

        assertEquals(ModulesCalled.GENERIC,
                new SummaryService(mock(GeminiService.class)).extractFieldsFromSummary(summary).modules());
    }

    @Test void processarDicaSummaryAlsoComesFromClaude() {
        ClaudeService claude = mock(ClaudeService.class);
        when(claude.generateOnce(anyString(), anyList(), anyLong(), any(Duration.class), eq("summary")))
                .thenReturn(Optional.of(FULL_SUMMARY));
        GeminiService gemini = mock(GeminiService.class);

        var dto = new SummaryService(gemini, claude).createDtoSummary("Cliente: rejeição 539", null);

        assertEquals(FULL_SUMMARY, dto.fullSummary());
        assertEquals("O cliente tentou emitir a NF-e e recebeu a rejeição 539, impedindo a venda.", dto.problem());
        verifyNoInteractions(gemini);
    }
}
