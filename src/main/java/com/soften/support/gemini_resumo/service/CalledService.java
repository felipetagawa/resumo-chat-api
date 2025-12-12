package com.soften.support.gemini_resumo.service;

import com.soften.support.gemini_resumo.models.dtos.*;
import com.soften.support.gemini_resumo.models.enums.ModulesCalled;
import com.soften.support.gemini_resumo.models.entities.CalledEntity;
import com.soften.support.gemini_resumo.repositorys.CalledRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CalledService {

    private final CalledRepository calledRepository;
    private final SummaryService summaryService;
    private final SuggestionService suggestionService;

    public CalledService(CalledRepository calledRepository,
                         SummaryService summaryService,
                         SuggestionService suggestionService) {
        this.calledRepository = calledRepository;
        this.summaryService = summaryService;
        this.suggestionService = suggestionService;
    }

    public CalledEntity SaveCall(String summary) {
        FormatSummary format = summaryService.extractFieldsFromSummary(summary);

        CalledEntity entity = new CalledEntity();
        entity.setProblem(format.problem());
        entity.setSolution(format.solution());
        entity.setUpsell(format.upsell());
        entity.setPrints(format.prints());
        entity.setMoodClient(format.mood());
        entity.setModulesCalled(format.modules());

        return calledRepository.save(entity);
    }

    public SummaryDto generateSummaryWithoutSaving(String textCalled) {
        return summaryService.createDtoSummary(textCalled);
    }

    public TipResponseDto processFullTip(String textCalled) {
        try {
            SummaryDto summaryDto = generateSummaryWithoutSaving(textCalled);
            FormatSummary formatSummary = summaryDto.formatSummary();

            List<ModulesCalled> SearchModules = new ArrayList<>();
            if (formatSummary.modules() != null) {
                SearchModules.add(formatSummary.modules());
            }
            SearchModules.add(ModulesCalled.GENERIC);

            List<CalledEntity> relatedCalls = calledRepository.findByModulesCalledIn(SearchModules);

            if (relatedCalls.isEmpty()) {
                return createResponseWithoutHistory(summaryDto);
            }

            List<CalledEntity> filteredCalls = suggestionService.filterCallsBySimilarity(
                    relatedCalls,
                    formatSummary.problem()
            );

            if (filteredCalls.isEmpty()) {
                return createResponseWithHistoryWithoutSimilarity(summaryDto, relatedCalls.size());
            }

            List<String> solutions = filteredCalls.stream()
                    .map(CalledEntity::getSolution)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toList());

            List<String> tips = suggestionService.generateResolutionTipsList(solutions, formatSummary.problem());

            return TipResponseDto.builder()
                    .summary(summaryDto)
                    .problemDetected(formatSummary.problem())
                    .moduleDetected(formatSummary.modules() != null ?
                            formatSummary.modules().name() : "GENERIC")
                    .SimilarTagsFound(relatedCalls.size())
                    .solutionsAnalyzed(solutions.size())
                    .tips(tips)
                    .status("SUCESS")
                    .build();

        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    private TipResponseDto createResponseWithoutHistory(SummaryDto summaryDto) {
        return TipResponseDto.builder()
                .summary(summaryDto)
                .problemDetected(summaryDto.problem())
                .moduleDetected(summaryDto.module() != null ?
                        summaryDto.module().name() : "GENERIC")
                .SimilarTagsFound(0)
                .solutionsAnalyzed(0)
                .tips(List.of(
                        "Não foram encontrados chamados históricos relacionados a este módulo.",
                        "O problema foi analisado e categorizado automaticamente.",
                        "Consulte a solução apresentada no resumo para verificar os passos recomendados."
                ))
                .status("NO_HISTORY")
                .build();
    }

    private TipResponseDto createResponseWithHistoryWithoutSimilarity(SummaryDto summaryDto, int totalCalls) {
        return TipResponseDto.builder()
                .summary(summaryDto)
                .problemDetected(summaryDto.problem())
                .moduleDetected(summaryDto.module() != null ?
                        summaryDto.module().name() : "GENERIC")
                .SimilarTagsFound(totalCalls)
                .solutionsAnalyzed(0)
                .tips(List.of(
                        "Foram encontrados " + totalCalls + " chamados no módulo, mas nenhum com problema similar.",
                        "O problema parece ser específico ou com características únicas.",
                        "Consulte a solução apresentada no resumo para verificar os passos recomendados."
                ))
                .status("NO_SIMILARITY")
                .build();
    }

    private TipResponseDto createErrorResponse(Exception e) {
        return TipResponseDto.builder()
                .status("ERROR: " + e.getMessage())
                .tips(List.of("Não foi possível processar as dicas no momento."))
                .build();
    }
}