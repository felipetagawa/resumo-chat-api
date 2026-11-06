package com.soften.support.gemini_resumo.service;

import com.soften.support.gemini_resumo.config.GeminiApiProperties;
import com.soften.support.gemini_resumo.config.RestTemplateConfig;
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

    private static ClaudeService configuredClaude() {
        ClaudeService claude = mock(ClaudeService.class);
        when(claude.isConfigured()).thenReturn(true);
        return claude;
    }

    @SuppressWarnings("unchecked")
    @Test void claudeWritesTheLabelWithTheSamePolicyAndGeminiIsNotCalled() {
        ClaudeService claude = configuredClaude();
        when(claude.generateOnce(anyString(), anyList(), anyLong(), any(Duration.class), eq("problem_line")))
                .thenReturn(Optional.of("NF-e rejeição 539"));
        GeminiService gemini = mock(GeminiService.class);

        assertEquals("NF-e rejeição 539", new ProblemLineService(gemini, claude).line("rejeição 539 ao emitir"));

        ArgumentCaptor<List<ClaudeService.Turn>> turns = ArgumentCaptor.forClass(List.class);
        verify(claude).generateOnce(eq(GeminiService.PROBLEM_LINE_POLICY), turns.capture(),
                eq(ProblemLineService.CLAUDE_MAX_TOKENS), eq(ProblemLineService.CLAUDE_TIMEOUT), eq("problem_line"));
        assertEquals(List.of(new ClaudeService.Turn(true, "MENSAGENS DO CLIENTE:\nrejeição 539 ao emitir")), turns.getValue());
        verifyNoInteractions(gemini);
    }

    @Test void geminiKeepsItsTwoAttemptsWhenClaudeIsNotConfigured() {
        GeminiService gemini = mock(GeminiService.class);
        when(gemini.generateProblemLine("erro no xml")).thenReturn("Erro no XML");

        assertEquals("Erro no XML", new ProblemLineService(gemini).line("erro no xml"));
        assertEquals("Erro no XML", new ProblemLineService(gemini, null).line("erro no xml"));
        // In production ClaudeService is always a bean; without ANTHROPIC_API_KEY it is just unconfigured.
        assertEquals("Erro no XML", new ProblemLineService(gemini, mock(ClaudeService.class)).line("erro no xml"));
        verify(gemini, never()).generateProblemLine(anyString(), anyInt());
    }

    @Test void afterClaudeFailsOrTimesOutGeminiGetsASingleAttempt() {
        ClaudeService claude = configuredClaude();
        when(claude.generateOnce(anyString(), anyList(), anyLong(), any(Duration.class), anyString()))
                .thenReturn(Optional.empty());
        GeminiService gemini = mock(GeminiService.class);
        when(gemini.generateProblemLine("erro no xml", 1)).thenReturn("Erro no XML");

        assertEquals("Erro no XML", new ProblemLineService(gemini, claude).line("erro no xml"));
        verify(gemini, never()).generateProblemLine(anyString());
    }

    @Test void oneClaudeAttemptThenOneGeminiAttemptFitTheExtensionTenSecondAbort() {
        // Claude's timeout covers the whole call; Gemini's attempt is a connect plus a read, at the highest read
        // timeout GeminiApiProperties allows.
        GeminiApiProperties properties = new GeminiApiProperties();
        properties.setSmartReplyReadTimeoutMillis(Integer.MAX_VALUE);
        Duration worst = ProblemLineService.CLAUDE_TIMEOUT
                .plus(RestTemplateConfig.SMART_REPLY_CONNECT_TIMEOUT)
                .plusMillis(properties.getSafeSmartReplyReadTimeoutMillis());
        assertTrue(worst.compareTo(Duration.ofSeconds(10)) < 0, "worst case " + worst);
    }
}
