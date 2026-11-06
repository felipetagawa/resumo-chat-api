package com.soften.support.gemini_resumo.service;

import com.soften.support.gemini_resumo.docs.ClaudeService;
import com.soften.support.gemini_resumo.docs.DocsIndex;
import com.soften.support.gemini_resumo.dto.SmartReplyRequest;
import com.soften.support.gemini_resumo.dto.SmartReplyResponse;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** "Sugerir resposta" as a chat: the technician's turns, the manual only when it matters, only cited pages. */
class SmartReplyChatTest {
    static final String MANUAL_539 = "https://d/539";
    static final String CUSTOMER_539 = "Cliente: deu rejeição 539 duplicidade na NF-e";

    static List<ClaudeService.Turn> chat(String... texts) {
        List<ClaudeService.Turn> out = new java.util.ArrayList<>();
        for (int i = 0; i < texts.length; i++) out.add(new ClaudeService.Turn(i % 2 == 0, texts[i]));
        return out;
    }

    static SmartReplyRequest request(String conversation, String intent, List<ClaudeService.Turn> messages) {
        return new SmartReplyRequest(conversation, null, null, List.of(), false, null, intent, List.of(), messages);
    }

    /** Runs the service with Claude answering {@code modelText}; returns the response and the data JSON Claude saw. */
    @SuppressWarnings("unchecked")
    static Object[] run(SmartReplyRequest request, String modelText) throws Exception {
        ClaudeService claude = mock(ClaudeService.class);
        when(claude.generate(anyString(), anyList(), anyLong(), any(Duration.class), eq("smart_reply")))
                .thenReturn(Optional.of(modelText));
        SmartReplyResponse response = new SmartReplyService(mock(GeminiService.class), claude, SmartReplyDocsTest.index()).reply(request);
        ArgumentCaptor<List<ClaudeService.Turn>> turns = ArgumentCaptor.forClass(List.class);
        verify(claude).generate(anyString(), turns.capture(), anyLong(), any(), eq("smart_reply"));
        String data = turns.getValue().get(0).text();
        return new Object[]{response, new JSONObject(data.substring(data.indexOf('{')))};
    }

    static List<String> urls(SmartReplyResponse response) {
        return response.sources().stream().map(SmartReplyResponse.Source::url).toList();
    }

    @Test void chatTravelsAsDataWithAutorAndTexto() throws Exception {
        var out = run(request("Cliente: oi, bom dia", "resolver", chat("Resolver", "Me manda um print?", "ela já mandou")),
                "Recebi, vou olhar.");
        JSONArray chat = ((JSONObject) out[1]).getJSONArray("chat");
        assertEquals(3, chat.length());
        assertEquals("tecnico", chat.getJSONObject(0).getString("autor"));
        assertEquals("Resolver", chat.getJSONObject(0).getString("texto"));
        assertEquals("assistente", chat.getJSONObject(1).getString("autor"));
        assertEquals("Me manda um print?", chat.getJSONObject(1).getString("texto"));
        assertEquals("tecnico", chat.getJSONObject(2).getString("autor"));
        assertEquals("ela já mandou", chat.getJSONObject(2).getString("texto"));

        var old = run(request("Cliente: oi, bom dia", null, List.of()), "Bom dia! Em que posso ajudar?");
        assertFalse(((JSONObject) old[1]).has("chat"), "no chat key for the card-era payload");
    }

    @Test void technicianAskFindsTheManualEvenWhenTheCustomerSaidLittle() throws Exception {
        var out = run(request("Cliente: oi", null, chat("como resolver rejeição 539 duplicidade")),
                "Consulta a chave na SEFAZ.\nFONTES: 1");
        JSONObject data = (JSONObject) out[1];
        assertEquals(1, data.getJSONArray("documentacao").getJSONObject(0).getInt("n"));
        assertEquals(List.of(MANUAL_539), urls((SmartReplyResponse) out[0]));
    }

    @Test void cobrarAndEncerrarNeverSearchTheManual() throws Exception {
        for (String intent : List.of("encerrar", "cobrar_retorno")) {
            var out = run(request(CUSTOMER_539, intent, chat("Encerrar")), "Resolvido, até mais!\nFONTES: 1");
            assertFalse(((JSONObject) out[1]).has("documentacao"), intent + " must not search the manual");
            assertTrue(((SmartReplyResponse) out[0]).sources().isEmpty(), intent + " has no sources");
        }
        var hard = run(request(CUSTOMER_539, "cliente_dificil", List.of()), "Vamos resolver agora.\nFONTES: 1");
        assertTrue(((JSONObject) hard[1]).has("documentacao"), "cliente_dificil still searches");
    }

    @Test void onlyCitedPagesComeBackAndTheControlLineIsStripped() throws Exception {
        var cited = run(request(CUSTOMER_539, null, List.of()), "Consulta a chave na SEFAZ.\nFONTES: 1");
        assertEquals("Consulta a chave na SEFAZ.", ((SmartReplyResponse) cited[0]).reply());
        assertEquals(List.of(MANUAL_539), urls((SmartReplyResponse) cited[0]));

        for (String text : List.of("Me manda um print?\nFONTES: nenhuma", "Me manda um print?", "Me manda um print?\nFONTES: 7")) {
            var out = run(request(CUSTOMER_539, null, List.of()), text);
            assertEquals("Me manda um print?", ((SmartReplyResponse) out[0]).reply());
            assertTrue(((SmartReplyResponse) out[0]).sources().isEmpty(), "uncited or unknown pages are not sources: " + text);
        }
    }

    @Test void oddControlLinesAreAlwaysStripped() throws Exception {
        var lower = run(request(CUSTOMER_539, null, List.of()), "Consulta a chave.\nfontes:1");
        assertEquals("Consulta a chave.", ((SmartReplyResponse) lower[0]).reply());
        assertEquals(List.of(MANUAL_539), urls((SmartReplyResponse) lower[0]));

        var spaced = run(request(CUSTOMER_539, null, List.of()), "Consulta a chave.\n  FONTES :  1 , 1 ");
        assertEquals("Consulta a chave.", ((SmartReplyResponse) spaced[0]).reply());
        assertEquals(List.of(MANUAL_539), urls((SmartReplyResponse) spaced[0]), "deduped by url");

        var twice = run(request(CUSTOMER_539, null, List.of()), "Primeiro passo.\nFONTES: 1\nSegundo passo.\nFONTES: nenhuma");
        SmartReplyResponse response = (SmartReplyResponse) twice[0];
        assertFalse(response.reply().toUpperCase().contains("FONTES"));
        assertEquals("Primeiro passo.\nSegundo passo.", response.reply());
        assertTrue(response.sources().isEmpty(), "numbers come from the last control line");
    }

    @Test void anInlineControlLineAtTheEndIsStrippedToo() throws Exception {
        var inline = run(request(CUSTOMER_539, null, List.of()), "Consulte a chave na SEFAZ. FONTES: 1");
        assertEquals("Consulte a chave na SEFAZ.", ((SmartReplyResponse) inline[0]).reply());
        assertEquals(List.of(MANUAL_539), urls((SmartReplyResponse) inline[0]));

        var none = run(request(CUSTOMER_539, null, List.of()), "Me manda um print? fontes: nenhuma");
        assertEquals("Me manda um print?", ((SmartReplyResponse) none[0]).reply());
        assertTrue(((SmartReplyResponse) none[0]).sources().isEmpty());
    }

    @Test void aCustomerFacingFontesLineIsNotAControlLine() throws Exception {
        var out = run(request(CUSTOMER_539, null, List.of()), "Fontes: portal da SEFAZ e manual do sistema.\nMe avisa se der certo.");
        assertEquals("Fontes: portal da SEFAZ e manual do sistema.\nMe avisa se der certo.", ((SmartReplyResponse) out[0]).reply());
        assertTrue(((SmartReplyResponse) out[0]).sources().isEmpty());

        var words = run(request(CUSTOMER_539, null, List.of()), "Confira nas fontes: do portal.");
        assertEquals("Confira nas fontes: do portal.", ((SmartReplyResponse) words[0]).reply());
    }

    @Test void geminiFallbackFollowsTheSameRules() throws Exception {
        ClaudeService claude = mock(ClaudeService.class);
        when(claude.generate(anyString(), anyList(), anyLong(), any(Duration.class), anyString())).thenReturn(Optional.empty());
        GeminiService gemini = mock(GeminiService.class);
        when(gemini.generateInteractive(anyString(), anyString(), anyBoolean())).thenReturn("Texto\nFONTES: 1");
        var response = new SmartReplyService(gemini, claude, SmartReplyDocsTest.index())
                .reply(request(CUSTOMER_539, null, chat("Resolver")));
        assertEquals("Texto", response.reply());
        assertEquals(List.of(MANUAL_539), urls(response));
    }

    @Test void policyExplainsChatAndFontes() {
        assertTrue(SmartReplyService.POLICY.contains("CHAT (chat)"));
        assertTrue(SmartReplyService.POLICY.contains("FONTES:"));
    }
}
