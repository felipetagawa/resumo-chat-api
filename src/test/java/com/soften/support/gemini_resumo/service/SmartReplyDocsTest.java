package com.soften.support.gemini_resumo.service;

import com.soften.support.gemini_resumo.docs.ClaudeService;
import com.soften.support.gemini_resumo.docs.DocsIndex;
import com.soften.support.gemini_resumo.dto.SmartReplyRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SmartReplyDocsTest {

    static DocsIndex index() throws Exception {
        return new DocsIndex(new ObjectMapper().readTree("""
                {"chunks":[{"t":"Rejeição 539 - Duplicidade de NF-e","b":"siem-nf-e","p":"Soften SIEM","u":"https://d/539","d":"2026-01-02",
                "x":"Consulte a chave de acesso da nota no portal da SEFAZ antes de reenviar."}]}"""));
    }

    @SuppressWarnings("unchecked")
    @Test void claudeGetsMatchingManualPassagesAndTheSourceComesBack() throws Exception {
        ClaudeService claude = mock(ClaudeService.class);
        when(claude.generate(anyString(), anyList(), anyLong(), any(Duration.class), eq("smart_reply")))
                .thenReturn(Optional.of("Consegue me mandar a chave de acesso da nota?\nFONTES: 1"));
        GeminiService gemini = mock(GeminiService.class);
        var request = new SmartReplyRequest("Cliente: deu rejeição 539 duplicidade na NF-e", null, null, List.of(), false);

        var response = new SmartReplyService(gemini, claude, index()).reply(request);

        assertEquals("Consegue me mandar a chave de acesso da nota?", response.reply());
        assertEquals("https://d/539", response.sources().get(0).url());
        ArgumentCaptor<List<ClaudeService.Turn>> turns = ArgumentCaptor.forClass(List.class);
        verify(claude).generate(startsWith(SmartReplyService.POLICY.substring(0, 40)), turns.capture(), eq(512L), any(), eq("smart_reply"));
        String data = turns.getValue().get(0).text();
        JSONObject facts = new JSONObject(data.substring(data.indexOf('{')));
        assertEquals("Rejeição 539 - Duplicidade de NF-e", facts.getJSONArray("documentacao").getJSONObject(0).getString("titulo"));
        verifyNoInteractions(gemini);
    }

    @Test void theOpeningOfALongAttendanceStillFindsTheManual() throws Exception {
        String conversation = "Cliente: deu rejeição 539 duplicidade na NF-e\n"
                + "Você: vou verificar\n".repeat(200) + "Cliente: e aí, resolveu?";
        var hits = new SmartReplyService(mock(GeminiService.class), null, index()).relevantDocs(conversation);
        assertEquals("https://d/539", hits.get(0).passage().url());
    }

    @Test void unrelatedConversationSendsNoDocumentationAndGeminiCoversClaudeFailures() throws Exception {
        ClaudeService claude = mock(ClaudeService.class);
        when(claude.generate(anyString(), anyList(), anyLong(), any(Duration.class), anyString())).thenReturn(Optional.empty());
        GeminiService gemini = mock(GeminiService.class);
        when(gemini.generateInteractive(anyString(), anyString(), anyBoolean())).thenReturn("Pode me mandar um print?");
        var response = new SmartReplyService(gemini, claude, index())
                .reply(new SmartReplyRequest("Cliente: oi, bom dia", null, null, List.of(), false));
        assertEquals("Pode me mandar um print?", response.reply());
        assertTrue(response.sources().isEmpty());
        ArgumentCaptor<String> data = ArgumentCaptor.forClass(String.class);
        verify(gemini).generateInteractive(anyString(), data.capture(), eq(false));
        assertFalse(new JSONObject(data.getValue()).has("documentacao"));
    }
}
