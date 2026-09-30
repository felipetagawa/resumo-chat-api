package com.soften.support.gemini_resumo.models.dtos;

import java.util.List;

public record ProductClassificationResponse(
        String mode,
        List<ProductSuggestionDto> suggestions,
        double confidence,
        double unclearProbability,
        long latencyMs
) {
}
