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
            System.out.println("=== INICIANDO PROCESSAMENTO DE DICA ===");

            SummaryDto summaryDto = generateSummaryWithoutSaving(textCalled);
            FormatSummary formatSummary = summaryDto.formatSummary();

            System.out.println("Problema atual: " + formatSummary.problem());
            System.out.println("Módulo atual: " + formatSummary.modules());

            List<ModulesCalled> SearchModules = new ArrayList<>();
            if (formatSummary.modules() != null) {
                SearchModules.add(formatSummary.modules());
            }
            SearchModules.add(ModulesCalled.GENERIC);

            System.out.println("Buscando chamados nos módulos: " + SearchModules);
            List<CalledEntity> relatedCalls = calledRepository.findByModulesCalledIn(SearchModules);
            System.out.println("Chamados relacionados encontrados: " + relatedCalls.size());

            if (!relatedCalls.isEmpty()) {
                System.out.println("=== CHAMADOS ENCONTRADOS ===");
                for (int i = 0; i < Math.min(5, relatedCalls.size()); i++) {
                    CalledEntity call = relatedCalls.get(i);
                    System.out.println("ID: " + call.getId());
                    System.out.println("Problema: " + call.getProblem());
                    System.out.println("Solução: " + (call.getSolution() != null ?
                            call.getSolution().substring(0, Math.min(50, call.getSolution().length())) : "NULL"));
                    System.out.println("Módulo: " + call.getModulesCalled());
                    System.out.println("---");
                }
            }

            List<CalledEntity> filteredCalls = suggestionService.filterCallsBySimilarity(
                    relatedCalls,
                    formatSummary.problem()
            );

            System.out.println("Chamados filtrados por similaridade: " + filteredCalls.size());

            if (!filteredCalls.isEmpty()) {
                System.out.println("=== CHAMADOS SIMILARES ENCONTRADOS ===");
                for (CalledEntity call : filteredCalls) {
                    System.out.println("ID Similar: " + call.getId());
                    System.out.println("Problema Similar: " + call.getProblem());
                    System.out.println("Solução Similar: " + (call.getSolution() != null ?
                            call.getSolution().substring(0, Math.min(100, call.getSolution().length())) : "VAZIA/NULL"));
                    System.out.println("---");
                }
            }

            if (filteredCalls.isEmpty()) {
                System.out.println("Nenhum chamado similar encontrado!");
                return createResponseWithHistoryWithoutSimilarity(summaryDto, relatedCalls.size());
            }

            List<String> solutions = filteredCalls.stream()
                    .map(CalledEntity::getSolution)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toList());

            System.out.println("Soluções extraídas: " + solutions.size());

            if (!solutions.isEmpty()) {
                System.out.println("=== SOLUÇÕES EXTRAÍDAS ===");
                for (int i = 0; i < solutions.size(); i++) {
                    System.out.println("Solução " + (i+1) + ": " +
                            solutions.get(i).substring(0, Math.min(80, solutions.get(i).length())) + "...");
                }
            } else {
                System.out.println("ATENÇÃO: Nenhuma solução com texto encontrada!");
                System.out.println("Total de chamados filtrados: " + filteredCalls.size());

                for (CalledEntity call : filteredCalls) {
                    String sol = call.getSolution();
                    System.out.println("Chamado ID " + call.getId() + " - Solução: " +
                            (sol == null ? "NULL" : sol.isEmpty() ? "VAZIA" : "Tem " + sol.length() + " caracteres"));
                }
            }

            List<String> tips = suggestionService.generateResolutionTipsList(solutions, formatSummary.problem());

            System.out.println("Dicas geradas: " + tips.size());

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
            System.err.println("ERRO no processFullTip: " + e.getMessage());
            e.printStackTrace();
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