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
import java.util.stream.Collectors;

@Service
public class SummaryService {

    // Room for low-effort thinking plus a long summary (~1k tokens); billed only on what is used.
    static final long CLAUDE_MAX_TOKENS = 4096;
    // Gemini gets 8s per attempt for the same job. One Claude attempt, so 12s at worst before Gemini.
    static final Duration CLAUDE_TIMEOUT = Duration.ofSeconds(12);
    // Every label the prompt asks for, bold as extractFieldsFromSummary expects. MÓDULO comes last,
    // so a reply cut at max_tokens misses it.
    private static final List<String> SECTIONS = List.of("PROBLEMA / DÚVIDA:", "SOLUÇÃO APRESENTADA:",
            "OPORTUNIDADE DE UPSELL:", "PRINTS DE ERRO OU DE MENSAGENS RELEVANTES:", "HUMOR DO CLIENTE:", "MÓDULO:");
    // A field ends at the next of those labels, not at any '**': Claude bolds words inside values.
    private static final String NEXT_LABEL = SECTIONS.stream().map(label -> Pattern.quote("**" + label + "**"))
            .collect(Collectors.joining("|", "(?=", "|$)"));

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
        String summary = claude == null ? null : claude.generateOnce(GeminiService.createSummaryPrompt(),
                List.of(new ClaudeService.Turn(true, GeminiService.buildSummaryInput(textService, promptComplement))),
                CLAUDE_MAX_TOKENS, CLAUDE_TIMEOUT, "summary")
                .filter(SummaryService::hasEverySection).orElse(null);
        return summary != null ? summary : geminiService.generateSummary(textService, promptComplement);
    }

    private static boolean hasEverySection(String summary) {
        return SECTIONS.stream().allMatch(label -> summary.contains("**" + label + "**"));
    }

    public FormatSummary extractFieldsFromSummary(String summaryComplete) {
        String problem = extractField(summaryComplete, "PROBLEMA / DÚVIDA:");
        String solution = extractField(summaryComplete, "SOLUÇÃO APRESENTADA:");
        String upsell = extractField(summaryComplete, "OPORTUNIDADE DE UPSELL:");
        String prints = extractField(summaryComplete, "PRINTS DE ERRO OU DE MENSAGENS RELEVANTES:");
        String humor = extractField(summaryComplete, "HUMOR DO CLIENTE:");
        // The module is one category; a note the model adds below it must not be mapped.
        String module = extractField(summaryComplete, "MÓDULO:").lines()
                .map(String::trim).filter(line -> !line.isEmpty()).findFirst().orElse("");

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

    private String extractField(String text, String label) {
        Matcher m = Pattern.compile(Pattern.quote("**" + label + "**") + "\\s*([\\s\\S]*?)" + NEXT_LABEL).matcher(text);
        // Bold kept inside a value (e.g. **NEUTRO.**) would show up as asterisks in Processar dica.
        return m.find() ? m.group(1).replace("**", "").trim() : "";
    }

    private MoodClient mapMood(String humor) {
        String h = humor.toUpperCase();
        if (h.contains("BOM")) return MoodClient.GOOD;
        if (h.contains("NEUTRO")) return MoodClient.NEUTRAL;
        if (h.contains("IRRITADO")) return MoodClient.IRRITATED;
        return MoodClient.GOOD;
    }
}
