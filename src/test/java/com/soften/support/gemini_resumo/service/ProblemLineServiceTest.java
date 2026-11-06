package com.soften.support.gemini_resumo.service;

import com.soften.support.gemini_resumo.docs.ClaudeService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProblemLineServiceTest {

    @SuppressWarnings("unchecked")
    @Test void claudeWritesTheLabelWithTheSamePolicyAndGeminiIsNotCalled() {
        ClaudeService claude = mock(ClaudeService.class);
        when(claude.generate(anyString(), anyList(), anyLong(), any(Duration.class), eq("problem_line")))
                .thenReturn(Optional.of("NF-e rejeição 539"));
        GeminiService gemini = mock(GeminiService.class);

        assertEquals("NF-e rejeição 539", new ProblemLineService(gemini, claude).line("rejeição 539 ao emitir"));

        ArgumentCaptor<List<ClaudeService.Turn>> turns = ArgumentCaptor.forClass(List.class);
        verify(claude).generate(eq(GeminiService.PROBLEM_LINE_POLICY), turns.capture(),
                eq(ProblemLineService.CLAUDE_MAX_TOKENS), eq(ProblemLineService.CLAUDE_TIMEOUT), eq("problem_line"));
        assertEquals(List.of(new ClaudeService.Turn(true, "MENSAGENS DO CLIENTE:\nrejeição 539 ao emitir")), turns.getValue());
        verifyNoInteractions(gemini);
    }

    @Test void geminiWritesTheLabelWhenClaudeIsNotConfigured() {
        GeminiService gemini = mock(GeminiService.class);
        when(gemini.generateProblemLine("erro no xml")).thenReturn("Erro no XML");

        assertEquals("Erro no XML", new ProblemLineService(gemini).line("erro no xml"));
        assertEquals("Erro no XML", new ProblemLineService(gemini, null).line("erro no xml"));
    }

    @Test void geminiWritesTheLabelWhenClaudeFailsOrTimesOut() {
        ClaudeService claude = mock(ClaudeService.class);
        when(claude.generate(anyString(), anyList(), anyLong(), any(Duration.class), anyString()))
                .thenReturn(Optional.empty());
        GeminiService gemini = mock(GeminiService.class);
        when(gemini.generateProblemLine("erro no xml")).thenReturn("Erro no XML");

        assertEquals("Erro no XML", new ProblemLineService(gemini, claude).line("erro no xml"));
    }

    @Test void claudeAndItsRetryPlusGeminiFitTheExtensionTenSecondAbort() {
        // The SDK retries a timeout once; Gemini then gets up to 4s for the label.
        assertTrue(ProblemLineService.CLAUDE_TIMEOUT.multipliedBy(2).plusSeconds(4)
                .compareTo(Duration.ofSeconds(10)) <= 0);
    }
}
