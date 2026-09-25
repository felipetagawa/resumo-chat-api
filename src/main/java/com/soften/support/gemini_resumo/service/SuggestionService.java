package com.soften.support.gemini_resumo.service;

import com.soften.support.gemini_resumo.models.dtos.SummaryDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class SuggestionService {
    private static final int MAX_TIPS = 6;
    private static final int MAX_TIP_LENGTH = 400;
    private static final Pattern LIST_MARKER = Pattern.compile("^(?:[-*•]|\\d+[.)])\\s+(.+)$");
    private final GeminiService geminiService;

    public SuggestionService(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    public List<String> generateTips(String currentConversation, SummaryDto analysis) {
        String prompt = """
                Você é um analista técnico de suporte. Gere de 3 a 6 dicas práticas para o problema atual.
                Use apenas o atendimento e a análise abaixo. Não afirme ter consultado chamados anteriores.
                A seção AÇÕES JÁ REALIZADAS descreve o que foi feito ou orientado no atendimento.
                Não repita essas ações como recomendações futuras. Priorize próximos passos ainda não realizados,
                verificações complementares, hipóteses alternativas e validações relevantes para o problema.
                Se o problema já foi resolvido, sugira apenas validações complementares pertinentes.
                Não invente fatos nem garanta uma solução. Se faltarem dados, indique verificações seguras e específicas.
                Responda em português,
                com uma dica por linha, iniciada por '- '. Não inclua título.

                PROBLEMA ATUAL:
                """ + safe(analysis.problem()) + "\n\nAÇÕES JÁ REALIZADAS NO ATENDIMENTO:\n"
                + safe(analysis.solution()) + "\n\nCONTEXTO DO RESUMO ATUAL:\n"
                + safe(analysis.fullSummary()) + "\n\nATENDIMENTO ATUAL:\n" + safe(currentConversation);

        String answer = geminiService.ask(prompt);
        if (answer == null || answer.isBlank()) {
            throw new GeminiIntegrationException(
                    "Não foi possível gerar dicas no momento.",
                    org.springframework.http.HttpStatus.BAD_GATEWAY,
                    new IllegalStateException("Resposta vazia da Gemini para dicas."));
        }

        List<String> tips = new ArrayList<>();
        boolean structuredResponse = false;
        for (String line : answer.split("\\R")) {
            var matcher = LIST_MARKER.matcher(line.trim());
            if (matcher.matches()) {
                structuredResponse = true;
                addTip(tips, matcher.group(1), analysis.solution());
            }
        }
        if (!structuredResponse) {
            for (String paragraph : answer.split("(?:\\R\\s*){2,}|\\R")) {
                addTip(tips, paragraph, analysis.solution());
            }
        }
        if (tips.isEmpty()) {
            tips.add("Valide se o problema ainda ocorre após as ações registradas e confira o retorno atual antes de fazer novos ajustes.");
        }
        return List.copyOf(tips);
    }

    private void addTip(List<String> tips, String text, String completedActions) {
        String tip = text.trim().replaceAll("\\s+", " ");
        if (tip.isBlank() || tips.size() >= MAX_TIPS || sameAction(tip, completedActions)) {
            return;
        }
        tips.add(tip.length() > MAX_TIP_LENGTH ? tip.substring(0, MAX_TIP_LENGTH).trim() + "…" : tip);
    }

    private boolean sameAction(String tip, String completedActions) {
        String normalizedTip = normalize(tip);
        for (String sentence : safe(completedActions).split("[.!?]+")) {
            if (normalizedTip.equals(normalize(sentence))) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    private String safe(String text) {
        return text == null ? "Não informado." : text;
    }
}
