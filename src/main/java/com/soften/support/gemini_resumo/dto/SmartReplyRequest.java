package com.soften.support.gemini_resumo.dto;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.soften.support.gemini_resumo.docs.ClaudeService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** messages: the technician's chat with the assistant (user = technician, assistant = earlier replies); empty when absent. */
public record SmartReplyRequest(String conversation, String promptComplement, String replyInstruction,
        List<String> styleSamples, boolean regenerate, String currentReply, String intent, List<String> notices,
        List<ClaudeService.Turn> messages) {
    public static final int MAX_CONVERSATION = 20000, MAX_COMPLEMENT = 2000, MAX_INSTRUCTION = 600,
            MAX_SAMPLES = 8, MAX_SAMPLE = 300, MAX_CURRENT_REPLY = 1500, MAX_NOTICES = 5, MAX_NOTICE = 500;
    public static final int MAX_CHAT_TURNS = 12, MAX_CHAT_TURN = 1500, MAX_CHAT_TOTAL = 8000;
    /** The kind of message the technician asked for with one click; null is a free suggestion. */
    public static final Set<String> INTENTS = Set.of(
            "resolver", "pedir_info", "aviso", "cliente_dificil", "cobrar_retorno", "encerrar");

    public SmartReplyRequest {
        styleSamples = styleSamples == null ? List.of() : List.copyOf(styleSamples);
        notices = notices == null ? List.of() : List.copyOf(notices);
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    public SmartReplyRequest(String conversation, String promptComplement, String replyInstruction,
            List<String> styleSamples, boolean regenerate, String currentReply, String intent, List<String> notices) {
        this(conversation, promptComplement, replyInstruction, styleSamples, regenerate, currentReply, intent, notices, List.of());
    }

    public SmartReplyRequest(String conversation, String promptComplement, String replyInstruction,
            List<String> styleSamples, boolean regenerate, String currentReply) {
        this(conversation, promptComplement, replyInstruction, styleSamples, regenerate, currentReply, null, List.of());
    }

    public SmartReplyRequest(String conversation, String promptComplement, String replyInstruction,
            List<String> styleSamples, boolean regenerate) {
        this(conversation, promptComplement, replyInstruction, styleSamples, regenerate, null);
    }

    // All validation happens here, before Bean Validation could echo rejected text into logs.
    @JsonCreator
    public static SmartReplyRequest from(@JsonProperty("conversation") JsonNode conversation,
            @JsonProperty("promptComplement") JsonNode complement,
            @JsonProperty("replyInstruction") JsonNode replyInstruction,
            @JsonProperty("styleSamples") JsonNode styleSamples,
            @JsonProperty("regenerate") JsonNode regenerate,
            // The suggestion on screen (possibly hand-edited) that replyInstruction should revise.
            @JsonProperty("currentReply") JsonNode currentReply,
            @JsonProperty("intent") JsonNode intent,
            // "Avisos do dia" the technician keeps in the extension (SEFAZ unstable, maintenance...).
            @JsonProperty("notices") JsonNode notices,
            @JsonProperty("messages") JsonNode messages,
            @JsonProperty("profile") JsonNode legacyProfile,
            @JsonProperty("styleInstruction") JsonNode legacyStyle) {
        if (regenerate != null && !regenerate.isNull() && !regenerate.isBoolean())
            throw new IllegalArgumentException("regenerate deve ser booleano");
        // Extensions <= 1.5.2 still send these; accepted, type-checked and ignored.
        text(legacyProfile); text(legacyStyle);
        String c = text(conversation), pc = text(complement), ri = text(replyInstruction), cr = text(currentReply);
        if (c == null || c.isBlank() || c.length() > MAX_CONVERSATION) throw new IllegalArgumentException("Conversa inválida");
        if (pc != null && pc.length() > MAX_COMPLEMENT) throw new IllegalArgumentException("Adendo inválido");
        if (ri != null && ri.length() > MAX_INSTRUCTION) throw new IllegalArgumentException("Instrução avulsa inválida");
        if (cr != null && cr.length() > MAX_CURRENT_REPLY) throw new IllegalArgumentException("Resposta atual inválida");
        // A revision needs both the text and what to change in it.
        if (cr != null && !cr.isBlank() && (ri == null || ri.isBlank())) throw new IllegalArgumentException("Ajuste sem instrução");
        String in = text(intent);
        if (in != null && !INTENTS.contains(in)) throw new IllegalArgumentException("Intenção inválida");
        boolean again = regenerate != null && regenerate.asBoolean(false);
        List<ClaudeService.Turn> turns = chat(messages);
        // In the chat the technician adjusts by talking; the card-era adjust fields would contradict it.
        if (!turns.isEmpty() && ((ri != null && !ri.isBlank()) || (cr != null && !cr.isBlank()) || again))
            throw new IllegalArgumentException("Chat com campos de ajuste");
        return new SmartReplyRequest(c, pc, ri, samples(styleSamples), again,
                cr == null || cr.isBlank() ? null : cr.trim(), in, notices(notices), turns);
    }

    private static List<ClaudeService.Turn> chat(JsonNode node) {
        if (node == null || node.isNull()) return List.of();
        if (!node.isArray() || node.isEmpty() || node.size() > MAX_CHAT_TURNS) throw new IllegalArgumentException("Chat inválido");
        List<ClaudeService.Turn> out = new ArrayList<>();
        int total = 0;
        for (JsonNode item : node) {
            String role = item.path("role").asText("");
            JsonNode content = item.get("content");
            // Turns alternate, starting and ending with the technician.
            boolean user = out.size() % 2 == 0;
            if (!role.equals(user ? "user" : "assistant") || content == null || !content.isTextual())
                throw new IllegalArgumentException("Chat inválido");
            String text = content.textValue().trim();
            if (text.isEmpty() || text.length() > MAX_CHAT_TURN) throw new IllegalArgumentException("Chat inválido");
            total += text.length();
            out.add(new ClaudeService.Turn(user, text));
        }
        if (!out.get(out.size() - 1).fromUser() || total > MAX_CHAT_TOTAL) throw new IllegalArgumentException("Chat inválido");
        return out;
    }

    private static List<String> notices(JsonNode node) {
        if (node == null || node.isNull()) return List.of();
        if (!node.isArray() || node.size() > MAX_NOTICES) throw new IllegalArgumentException("Avisos inválidos");
        List<String> out = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isTextual() || item.textValue().isBlank() || item.textValue().length() > MAX_NOTICE)
                throw new IllegalArgumentException("Avisos inválidos");
            out.add(item.textValue().trim());
        }
        return out;
    }

    private static List<String> samples(JsonNode node) {
        if (node == null || node.isNull()) return List.of();
        if (!node.isArray() || node.size() > MAX_SAMPLES) throw new IllegalArgumentException("Amostras inválidas");
        List<String> out = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isTextual() || item.textValue().isBlank() || item.textValue().length() > MAX_SAMPLE)
                throw new IllegalArgumentException("Amostras inválidas");
            out.add(item.textValue().trim());
        }
        return out;
    }

    private static String text(JsonNode value) {
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw new IllegalArgumentException("Campo deve ser texto");
        return value.textValue();
    }

    @JsonAnySetter
    public void rejectUnknown(String name, Object value) { throw new IllegalArgumentException("Campo não permitido"); }
}
