package com.soften.support.gemini_resumo.models.dtos;

import com.soften.support.gemini_resumo.models.enums.ModulesCalled;
import com.soften.support.gemini_resumo.models.enums.MoodClient;

public record FormatSummary(
        String problem,
        String solution,
        String upsell,
        boolean prints,
        MoodClient mood,
        ModulesCalled modules
) {}
