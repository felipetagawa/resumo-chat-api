package com.soften.support.gemini_resumo.service;

import com.soften.support.gemini_resumo.models.dtos.FormatSummary;
import com.soften.support.gemini_resumo.models.dtos.SummaryDto;
import com.soften.support.gemini_resumo.models.enums.ModulesCalled;
import com.soften.support.gemini_resumo.models.enums.MoodClient;
import com.soften.support.gemini_resumo.utils.ModuleMapper;
import com.soften.support.gemini_resumo.docs.ClaudeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SummaryService {

    // Room for low-effort thinking plus a long summary (~1k tokens); billed only on what is used.
    static final long CLAUDE_MAX_TOKENS = 4096;
    // Gemini gets 8s per attempt for the same job; the SDK retries once, so ~24s at worst before Gemini.
    static final Duration CLAUDE_TIMEOUT = Duration.ofSeconds(12);
    // Every label the prompt asks for, bold as extractFieldsFromSummary expects. MÓDULO comes last,
    // so a reply cut at max_tokens misses it.
    private static final List<String> SECTIONS = List.of("PROBLEMA / DÚVIDA:", "SOLUÇÃO APRESENTADA:",
            "OPORTUNIDADE DE UPSELL:", "PRINTS DE ERRO OU DE MENSAGENS RELEVANTES:", "HUMOR DO CLIENTE:", "MÓDULO:");

    private final GeminiService geminiService;
    private final ClaudeService claude;

    public SummaryService(GeminiService geminiService) { this(geminiService, null); }

    @Autowired
    public SummaryService(GeminiService geminiService, ClaudeService claude) {
        this.geminiService = geminiService;
        this.claude = claude;
    }

    /** Gerar resumo and Processar dica: Claude first when configured; Gemini answers when it is absent, slow, failing or off-format. */
    public String summarize(String textService, String promptComplement) {
        String summary = claude == null ? null : claude.generate(GeminiService.createSummaryPrompt(),
                List.of(new ClaudeService.Turn(true, GeminiService.buildSummaryInput(textService, promptComplement))),
                CLAUDE_MAX_TOKENS, CLAUDE_TIMEOUT, "summary")
                .filter(SummaryService::hasEverySection).orElse(null);
        return summary != null ? summary : geminiService.generateSummary(textService, promptComplement);
    }

    private static boolean hasEverySection(String summary) {
        return SECTIONS.stream().allMatch(label -> summary.contains("**" + label + "**"));
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

    public SummaryDto createDtoSummary(String textCall, String promptComplement) {
        String summaryComplete = summarize(textCall, promptComplement);
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
