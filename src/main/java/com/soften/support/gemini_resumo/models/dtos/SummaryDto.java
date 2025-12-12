package com.soften.support.gemini_resumo.models.dtos;

import com.soften.support.gemini_resumo.models.enums.ModulesCalled;

public record SummaryDto(
        String fullSummary,
        ModulesCalled module,
        String problem,
        String solution,
        FormatSummary formatSummary
)
{}
