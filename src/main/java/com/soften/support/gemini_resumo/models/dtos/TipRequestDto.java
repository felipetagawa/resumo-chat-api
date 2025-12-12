package com.soften.support.gemini_resumo.models.dtos;

public record TipRequestDto(
        String texto,
        boolean incluirRelatorio
) {
    public TipRequestDto(String texto) {
        this(texto, false);
    }
}
