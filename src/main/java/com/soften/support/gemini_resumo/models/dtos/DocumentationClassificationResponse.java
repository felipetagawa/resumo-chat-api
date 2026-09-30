package com.soften.support.gemini_resumo.models.dtos;

import java.util.List;

public record DocumentationClassificationResponse(
        String mode,
        List<DocumentationSuggestionDto> suggestions,
        double confidence,
        double unclearProbability,
        long latencyMs
) {
}
