package com.soften.support.gemini_resumo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soften.support.gemini_resumo.dto.SmartReplyRequest;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SmartReplyIntentTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private SmartReplyRequest parse(Map<String, Object> body) throws Exception {
        return mapper.readValue(mapper.writeValueAsString(body), SmartReplyRequest.class);
    }

    @Test void intentAndNoticesTravelAsDataNeverAsPolicy() throws Exception {
        GeminiService gemini = mock(GeminiService.class);
        when(gemini.generateInteractive(anyString(), anyString(), anyBoolean())).thenReturn("A SEFAZ-MG está instável desde as 9h.");
        var request = parse(Map.of("conversation", "Cliente: a nota não autoriza", "intent", "aviso",
                "notices", List.of("SEFAZ-MG instável desde 9h; notas em contingência")));
        new SmartReplyService(gemini).reply(request);
        var policy = ArgumentCaptor.forClass(String.class);
        var data = ArgumentCaptor.forClass(String.class);
        verify(gemini).generateInteractive(policy.capture(), data.capture(), eq(false));
        assertTrue(policy.getValue().contains("INTENÇÃO (intent)"));
        assertTrue(policy.getValue().contains("- cliente_dificil:"));
        assertFalse(policy.getValue().contains("SEFAZ-MG"), "a notice never enters the policy text");
        JSONObject facts = new JSONObject(data.getValue());
        assertEquals("aviso", facts.getString("intent"));
        assertEquals("SEFAZ-MG instável desde 9h; notas em contingência", facts.getJSONArray("notices").getString(0));
    }

    @Test void rejectsUnknownIntentAndOversizedNotices() {
        assertThrows(Exception.class, () -> parse(Map.of("conversation", "oi", "intent", "enviar_sozinho")));
        assertThrows(Exception.class, () -> parse(Map.of("conversation", "oi", "notices", List.of("x".repeat(501)))));
        assertThrows(Exception.class, () -> parse(Map.of("conversation", "oi", "notices", List.of("a", "b", "c", "d", "e", "f"))));
        assertThrows(Exception.class, () -> parse(Map.of("conversation", "oi", "notices", "texto solto")));
    }

    @Test void withoutIntentOrNoticesTheDataStaysAsBefore() throws Exception {
        var request = parse(Map.of("conversation", "oi"));
        assertNull(request.intent());
        assertTrue(request.notices().isEmpty());
        for (String intent : SmartReplyRequest.INTENTS) {
            assertEquals(intent, parse(Map.of("conversation", "oi", "intent", intent)).intent());
        }
    }
}
