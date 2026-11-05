package com.soften.support.gemini_resumo.docs;

import com.soften.support.gemini_resumo.service.GeminiService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DocsChatServiceTest {

    @SuppressWarnings("unchecked")
    @Test void sendsNumberedPassagesWithTheLastQuestionAndReturnsSources() throws Exception {
        ClaudeService claude = mock(ClaudeService.class);
        when(claude.model()).thenReturn("claude-haiku-5-5");
        when(claude.generate(anyString(), anyList(), anyLong(), any(Duration.class), anyString()))
                .thenReturn(Optional.of("Consulte a chave na SEFAZ [1]."));
        GeminiService gemini = mock(GeminiService.class);
        DocsChatService service = new DocsChatService(DocsIndexTest.sample(), claude, gemini);

        var answer = service.chat(List.of(
                new ClaudeService.Turn(true, "rejeição 539 no SIEM"),
                new ClaudeService.Turn(false, "Consulte a chave [1]."),
                new ClaudeService.Turn(true, "e no GerencieAqui?")), null);

        assertEquals("Consulte a chave na SEFAZ [1].", answer.answer());
        assertEquals("claude-haiku-5-5", answer.model());
        assertFalse(answer.sources().isEmpty());
        assertEquals(1, answer.sources().get(0).n());

        ArgumentCaptor<List<ClaudeService.Turn>> turns = ArgumentCaptor.forClass(List.class);
        verify(claude).generate(eq(DocsChatService.SYSTEM), turns.capture(), anyLong(), any(), eq("docs_chat"));
        assertEquals(3, turns.getValue().size());
        String last = turns.getValue().get(2).text();
        // The follow-up still finds the 539 pages because the previous question joins the search.
        assertTrue(last.contains("<documentacao>") && last.contains("[1] Rejeição 539"), last);
        assertTrue(last.endsWith("Pergunta: e no GerencieAqui?"));
        verifyNoInteractions(gemini);
    }

    @Test void fallsBackToGeminiWhenClaudeIsUnavailable() throws Exception {
        ClaudeService claude = mock(ClaudeService.class);
        when(claude.generate(anyString(), anyList(), anyLong(), any(Duration.class), anyString())).thenReturn(Optional.empty());
        GeminiService gemini = mock(GeminiService.class);
        when(gemini.generateInteractive(eq(DocsChatService.SYSTEM), contains("rejeição 539"))).thenReturn("Resposta Gemini");
        var answer = new DocsChatService(DocsIndexTest.sample(), claude, gemini)
                .chat(List.of(new ClaudeService.Turn(true, "rejeição 539")), null);
        assertEquals("Resposta Gemini", answer.answer());
        assertEquals("gemini", answer.model());
    }

    @Test void rejectsMalformedConversations() {
        assertThrows(IllegalArgumentException.class, () -> DocsChatService.validate(List.of()));
        assertThrows(IllegalArgumentException.class, () -> DocsChatService.validate(
                List.of(new ClaudeService.Turn(false, "oi"))));
        assertThrows(IllegalArgumentException.class, () -> DocsChatService.validate(List.of(
                new ClaudeService.Turn(true, "a"), new ClaudeService.Turn(false, "b"))));
        assertThrows(IllegalArgumentException.class, () -> DocsChatService.validate(
                List.of(new ClaudeService.Turn(true, "x".repeat(2001)))));
        assertDoesNotThrow(() -> DocsChatService.validate(List.of(new ClaudeService.Turn(true, "ok"))));
    }
}
