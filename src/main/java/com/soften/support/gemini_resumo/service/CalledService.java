package com.soften.support.gemini_resumo.service;

import com.soften.support.gemini_resumo.models.dtos.SummaryDto;
import com.soften.support.gemini_resumo.models.dtos.TipResponseDto;
import org.springframework.stereotype.Service;

@Service
public class CalledService {
    private final SummaryService summaryService;
    private final SuggestionService suggestionService;

    public CalledService(SummaryService summaryService, SuggestionService suggestionService) {
        this.summaryService = summaryService;
        this.suggestionService = suggestionService;
    }

    public TipResponseDto processFullTip(String textCalled, String promptComplement) {
        if (textCalled == null || textCalled.isBlank()) {
            throw new IllegalArgumentException("Campo 'texto' não pode estar vazio.");
        }

        SummaryDto summary = summaryService.createDtoSummary(textCalled, promptComplement);
        String problem = summary.problem();
        String module = summary.module() == null ? "GENERIC" : summary.module().name();

        return TipResponseDto.builder()
                .summary(summary)
                .problemDetected(problem)
                .moduleDetected(module)
                .SimilarTagsFound(0)
                .solutionsAnalyzed(0)
                .tips(suggestionService.generateTips(textCalled, summary))
                .status("SUCESS")
                .build();
    }
}
