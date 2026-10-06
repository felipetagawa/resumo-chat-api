package com.soften.support.gemini_resumo.dto;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
public record SmartReplyRequest(@NotBlank @Size(max=20000) String conversation,
        @Size(max=2000) String promptComplement, @NotNull SmartReplyProfile profile, boolean regenerate) {
    @JsonCreator
    public static SmartReplyRequest from(@JsonProperty("conversation") JsonNode conversation,
            @JsonProperty("promptComplement") JsonNode complement,
            @JsonProperty("profile") JsonNode profile, @JsonProperty("regenerate") JsonNode regenerate) {
        if (regenerate != null && !regenerate.isNull() && !regenerate.isBoolean())
            throw new IllegalArgumentException("regenerate deve ser booleano");
        String p = text(profile);
        return new SmartReplyRequest(text(conversation), text(complement),
                p == null ? null : SmartReplyProfile.valueOf(p), regenerate != null && regenerate.asBoolean(false));
    }
    private static String text(JsonNode value) {
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw new IllegalArgumentException("Campo deve ser texto");
        return value.textValue();
    }
    @JsonAnySetter
    public void rejectUnknown(String name, Object value) { throw new IllegalArgumentException("Campo não permitido"); }
}
