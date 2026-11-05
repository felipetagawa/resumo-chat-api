package com.soften.support.gemini_resumo.dto;

import java.util.List;

/** sources: SoftenDocs pages given to the model as reference for this suggestion. */
public record SmartReplyResponse(String reply, List<Source> sources) {
    public record Source(String title, String url) {}
    public SmartReplyResponse(String reply) { this(reply, List.of()); }
    public SmartReplyResponse { sources = sources == null ? List.of() : List.copyOf(sources); }
}
