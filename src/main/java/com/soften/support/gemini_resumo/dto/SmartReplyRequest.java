package com.soften.support.gemini_resumo.dto;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

public record SmartReplyRequest(String conversation, String promptComplement, String replyInstruction,
        List<String> styleSamples, boolean regenerate, String currentReply) {
    public static final int MAX_CONVERSATION = 20000, MAX_COMPLEMENT = 2000, MAX_INSTRUCTION = 600,
            MAX_SAMPLES = 8, MAX_SAMPLE = 300, MAX_CURRENT_REPLY = 1500;

    public SmartReplyRequest {
        styleSamples = styleSamples == null ? List.of() : List.copyOf(styleSamples);
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
        return new SmartReplyRequest(c, pc, ri, samples(styleSamples), regenerate != null && regenerate.asBoolean(false),
                cr == null || cr.isBlank() ? null : cr.trim());
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
