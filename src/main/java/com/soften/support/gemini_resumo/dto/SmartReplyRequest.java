package com.soften.support.gemini_resumo.dto;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
public record SmartReplyRequest(@NotBlank @Size(max=20000) String conversation,
        @Size(max=2000) String promptComplement, @NotNull SmartReplyProfile profile,
        boolean regenerate, @Size(max=600) String styleInstruction, @Size(max=600) String replyInstruction) {
    // Preserve the native-profile constructor used by existing callers.
    public SmartReplyRequest(String conversation, String promptComplement, SmartReplyProfile profile, boolean regenerate) {
        this(conversation, promptComplement, profile, regenerate, null, null);
    }
    public SmartReplyRequest(String conversation, String promptComplement, SmartReplyProfile profile, boolean regenerate, String styleInstruction) {
        this(conversation, promptComplement, profile, regenerate, styleInstruction, null);
    }
    public boolean customStyleValid() {
        return profile != SmartReplyProfile.CUSTOM || (styleInstruction != null && !styleInstruction.isBlank());
    }
    @JsonCreator
    public static SmartReplyRequest from(@JsonProperty("conversation") JsonNode conversation,
            @JsonProperty("promptComplement") JsonNode complement,
            @JsonProperty("profile") JsonNode profile, @JsonProperty("regenerate") JsonNode regenerate,
            @JsonProperty("styleInstruction") JsonNode styleInstruction, @JsonProperty("replyInstruction") JsonNode replyInstruction) {
        if (regenerate != null && !regenerate.isNull() && !regenerate.isBoolean())
            throw new IllegalArgumentException("regenerate deve ser booleano");
        String p = text(profile);
        // Validate even for native profiles; the service explicitly ignores valid native styles.
        String instruction = text(styleInstruction);
        String reply = text(replyInstruction);
        // Reject before Bean Validation can include the rejected instruction in warning logs.
        if (reply != null && reply.length() > 600) throw new IllegalArgumentException("Instrução avulsa inválida");
        SmartReplyRequest request = new SmartReplyRequest(text(conversation), text(complement),
                p == null ? null : SmartReplyProfile.valueOf(p), regenerate != null && regenerate.asBoolean(false), instruction, reply);
        if (!request.customStyleValid()) throw new IllegalArgumentException("CUSTOM exige uma instrução de estilo não vazia");
        return request;
    }
    private static String text(JsonNode value) {
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw new IllegalArgumentException("Campo deve ser texto");
        return value.textValue();
    }
    @JsonAnySetter
    public void rejectUnknown(String name, Object value) { throw new IllegalArgumentException("Campo não permitido"); }
}
