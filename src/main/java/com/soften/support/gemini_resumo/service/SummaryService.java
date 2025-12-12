package com.soften.support.gemini_resumo.service;

import com.soften.support.gemini_resumo.models.dtos.FormatSummary;
import com.soften.support.gemini_resumo.models.dtos.SummaryDto;
import com.soften.support.gemini_resumo.models.enums.ModulesCalled;
import com.soften.support.gemini_resumo.models.enums.MoodClient;
import com.soften.support.gemini_resumo.utils.ModuleMapper;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SummaryService {

    private final GeminiService geminiService;

    public SummaryService(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    public String generateFormattedSummary(String textService) {
        String prompt = geminiService.getPromptSummary();
        return geminiService.generateSummary(textService, prompt);
    }

    public FormatSummary extractFieldsFromSummary(String summaryComplete) {
        String problem = extractField(summaryComplete, "\\*\\*PROBLEMA / DÚVIDA:\\*\\*\\s*([\\s\\S]*?)(?=\\*\\*|$)");
        String solution = extractField(summaryComplete, "\\*\\*SOLUÇÃO APRESENTADA:\\*\\*\\s*([\\s\\S]*?)(?=\\*\\*|$)");
        String upsell = extractField(summaryComplete, "\\*\\*OPORTUNIDADE DE UPSELL:\\*\\*\\s*([\\s\\S]*?)(?=\\*\\*|$)");
        String prints = extractField(summaryComplete, "\\*\\*PRINTS DE ERRO OU DE MENSAGENS RELEVANTES:\\*\\*\\s*([\\s\\S]*?)(?=\\*\\*|$)");
        String humor = extractField(summaryComplete, "\\*\\*HUMOR DO CLIENTE:\\*\\*\\s*([\\s\\S]*?)(?=\\*\\*|$)");
        String module = extractField(summaryComplete, "\\*\\*MÓDULO:\\*\\*\\s*([\\s\\S]*?)(?=\\*\\*|$)");

        boolean printsFlag = prints.trim().toLowerCase().startsWith("sim");
        MoodClient mood = mapMood(humor);
        ModulesCalled modulesCalled = ModuleMapper.map(module);

        return new FormatSummary(
                problem.trim(),
                solution.trim(),
                upsell.trim(),
                printsFlag,
                mood,
                modulesCalled
        );
    }

    public SummaryDto createDtoSummary(String textCall) {
        String summaryComplete = generateFormattedSummary(textCall);
        FormatSummary formatSummary = extractFieldsFromSummary(summaryComplete);

        return new SummaryDto(
                summaryComplete,
                formatSummary.modules(),
                formatSummary.problem(),
                formatSummary.solution(),
                formatSummary
        );
    }

    private String extractField(String text, String regex) {
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(text);
        if (m.find()) return m.group(1).trim();
        return "";
    }

    private MoodClient mapMood(String humor) {
        String h = humor.toUpperCase();
        if (h.contains("BOM")) return MoodClient.GOOD;
        if (h.contains("NEUTRO")) return MoodClient.NEUTRAL;
        if (h.contains("IRRITADO")) return MoodClient.IRRITATED;
        return MoodClient.GOOD;
    }
}