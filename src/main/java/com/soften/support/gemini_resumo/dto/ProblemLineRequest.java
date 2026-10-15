package com.soften.support.gemini_resumo.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record ProblemLineRequest(@NotBlank @Size(max = 1500) String mensagens) {}
