package com.soften.support.gemini_resumo.models.dtos;

public record ProductSuggestionDto(
        String productId,
        String product,
        double probability
) {
}
