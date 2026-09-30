package com.soften.support.gemini_resumo.models.dtos;

import java.util.List;

public record DocumentationClassificationRequest(
        String context,
        List<DocumentationCandidateDto> candidates
) {
}
