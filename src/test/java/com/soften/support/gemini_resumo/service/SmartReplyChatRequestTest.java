package com.soften.support.gemini_resumo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soften.support.gemini_resumo.dto.SmartReplyRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The technician's chat with the assistant ("Sugerir resposta" as a chat window). */
class SmartReplyChatRequestTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private SmartReplyRequest parse(Map<String, Object> body) throws Exception {
        return mapper.readValue(mapper.writeValueAsString(body), SmartReplyRequest.class);
    }

    private static Map<String, Object> turn(String role, Object content) {
        Map<String, Object> m = new HashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    private static List<Map<String, Object>> alternating(int count, String content) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < count; i++) out.add(turn(i % 2 == 0 ? "user" : "assistant", content));
        return out;
    }

    private Map<String, Object> chat(Object messages, Object... extra) {
        Map<String, Object> body = new HashMap<>();
        body.put("conversation", "Cliente: deu erro");
        body.put("messages", messages);
        for (int i = 0; i < extra.length; i += 2) body.put((String) extra[i], extra[i + 1]);
        return body;
    }

    @Test void parsesAlternatingChatEndingWithTheTechnician() throws Exception {
        var r = parse(Map.of("conversation", "Cliente: deu erro", "intent", "resolver", "messages", List.of(
                turn("user", "Resolver"),
                turn("assistant", "Me manda um print?"),
                turn("user", " ela já mandou "))));
        assertEquals(3, r.messages().size());
        assertTrue(r.messages().get(0).fromUser());
        assertFalse(r.messages().get(1).fromUser());
        assertEquals("ela já mandou", r.messages().get(2).text());
    }

    @Test void oldPayloadHasNoMessages() throws Exception {
        assertTrue(parse(Map.of("conversation", "Cliente: oi")).messages().isEmpty());
        assertTrue(new SmartReplyRequest("Cliente: oi", null, null, List.of(), false).messages().isEmpty());
    }

    @Test void rejectsBadChatShapes() {
        assertThrows(Exception.class, () -> parse(chat(List.of(turn("assistant", "oi"), turn("user", "a")))), "first turn assistant");
        assertThrows(Exception.class, () -> parse(chat(List.of(turn("user", "a"), turn("assistant", "b")))), "last turn assistant");
        assertThrows(Exception.class, () -> parse(chat(List.of(turn("user", "a"), turn("user", "b")))), "two user turns in a row");
        assertThrows(Exception.class, () -> parse(chat(List.of(turn("system", "a")))), "unknown role");
        assertThrows(Exception.class, () -> parse(chat(List.of(turn("user", 42)))), "content not text");
        assertThrows(Exception.class, () -> parse(chat(List.of(turn("user", "   ")))), "blank content");
        assertThrows(Exception.class, () -> parse(chat(alternating(13, "a"))), "13 turns");
        assertThrows(Exception.class, () -> parse(chat(List.of(turn("user", "x".repeat(1501))))), "turn over 1500");
        assertThrows(Exception.class, () -> parse(chat(alternating(7, "x".repeat(1500)))), "over 8000 in total");
        assertThrows(Exception.class, () -> parse(chat("texto solto")), "messages not an array");
        assertThrows(Exception.class, () -> parse(chat(List.of())), "empty chat");
    }

    @Test void chatAcceptsTheLimitsExactly() throws Exception {
        assertEquals(11, parse(chat(alternating(11, "a"))).messages().size());
        assertEquals(1, parse(chat(List.of(turn("user", "x".repeat(1500))))).messages().size());
        var big = alternating(5, "x".repeat(1500));
        big.add(turn("assistant", "y".repeat(250)));
        big.add(turn("user", "z".repeat(250)));
        assertEquals(7, parse(chat(big)).messages().size(), "exactly 8000 characters");
    }

    @Test void chatRejectsTheOldAdjustFields() throws Exception {
        var one = List.of(turn("user", "Resolver"));
        assertThrows(Exception.class, () -> parse(chat(one, "replyInstruction", "mais curto")));
        assertThrows(Exception.class, () -> parse(chat(one, "currentReply", "x", "replyInstruction", "y")));
        assertThrows(Exception.class, () -> parse(chat(one, "regenerate", true)));
        assertEquals(1, parse(chat(one, "regenerate", false)).messages().size());
    }
}
