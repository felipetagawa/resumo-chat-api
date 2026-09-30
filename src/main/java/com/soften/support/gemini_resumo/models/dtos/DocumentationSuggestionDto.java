package com.soften.support.gemini_resumo.models.dtos;

public record DocumentationSuggestionDto(
        String id,
        String label,
        double probability
) {
}
