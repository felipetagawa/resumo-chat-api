package com.soften.support.gemini_resumo.service;

import com.soften.support.gemini_resumo.models.entities.CalledEntity;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SuggestionService {

    private final GeminiService geminiService;

    public SuggestionService(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    public List<CalledEntity> filterCallsBySimilarity(List<CalledEntity> called, String currentProblem) {
        if (called == null || called.isEmpty() || currentProblem == null || currentProblem.isBlank()) {
            return Collections.emptyList();
        }

        try {
            List<CalledEntity> chamadosParaAnalise = called.size() > 50 ?
                    called.subList(0, 50) : called;

            String prompt = createPromptComparisonProblems(chamadosParaAnalise, currentProblem);
            String response = geminiService.ask(prompt);

            List<Long> idsSimilar = extractSimilarIds(response);

            return chamadosParaAnalise.stream()
                    .filter(c -> c.getId() != null && idsSimilar.contains(c.getId()))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            return called.stream()
                    .limit(3)
                    .collect(Collectors.toList());
        }
    }

    public List<String> generateResolutionTipsList(List<String> solutions, String currentProblem) {
        List<String> tips = new ArrayList<>();

        if (solutions == null || solutions.isEmpty()) {
            tips.add("Não foram encontradas soluções históricas para análise.");
            return tips;
        }

        try {
            String prompt = createPromptGenerationTips(solutions, currentProblem);
            String response = geminiService.ask(prompt);

            tips = extractCuesFromTheAnswer(response);

            if (tips.isEmpty()) {
                tips.add("**Análise de Soluções Históricas:**");
                for (int i = 0; i < Math.min(solutions.size(), 3); i++) {
                    tips.add((i + 1) + ". " + truncateText(solutions.get(i), 200));
                }
            }

            return tips;

        } catch (Exception e) {
            tips.add("Erro ao processar tips automaticamente.");
            tips.add("Soluções encontradas no histórico: " + solutions.size());
            return tips;
        }
    }

    private String createPromptComparisonProblems(List<CalledEntity> called, String currentProblem) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Você é um especialista técnico. Compare o problema atual com problemas históricos e identifique os mais similares.\n\n");

        prompt.append("PROBLEMA ATUAL:\n").append(currentProblem).append("\n\n");

        prompt.append("PROBLEMAS HISTÓRICOS (formato: ID | Problema):\n");
        for (CalledEntity calls : called) {
            prompt.append("ID: ").append(calls.getId())
                    .append(" | Problema: ").append(truncateText(calls.getProblem(), 150))
                    .append("\n");
        }

        prompt.append("\nINSTRUÇÕES:\n");
        prompt.append("1. Analise a similaridade com base em: contexto, sintomas, tipo de erro, operação envolvida\n");
        prompt.append("2. Selecione apenas os IDs dos problemas MAIS SIMILARES (máximo 5)\n");
        prompt.append("3. Responda APENAS com os IDs separados por vírgula\n");
        prompt.append("4. Exemplo de resposta: 123, 456, 789\n");
        prompt.append("5. Se nenhum for similar, responda: NENHUM");

        return prompt.toString();
    }

    private String createPromptGenerationTips(List<String> solutions, String currentProblem) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Você é um técnico sênior. Com base nas soluções aplicadas em problemas similares, gere tips práticas para resolver o problema atual.\n\n");

        prompt.append("PROBLEMA ATUAL:\n").append(currentProblem).append("\n\n");

        prompt.append("SOLUÇÕES APLICADAS EM CASOS SIMILARES:\n");
        for (int i = 0; i < Math.min(solutions.size(), 10); i++) {
            prompt.append("Solução ").append(i + 1).append(":\n")
                    .append(truncateText(solutions.get(i), 300))
                    .append("\n---\n");
        }

        prompt.append("\nINSTRUÇÕES PARA AS DICAS:\n");
        prompt.append("1. Extraia padrões comuns das soluções\n");
        prompt.append("2. Liste tips em tópicos numerados (máximo 8 itens)\n");
        prompt.append("3. Seja prático e objetivo\n");
        prompt.append("4. Inclua verificação de configurações comuns\n");
        prompt.append("5. Formato de resposta: cada dica em uma linha começando com •\n");
        prompt.append("6. Exemplo:\n• Verifique se o certificado está instalado\n• Confirme a versão do módulo\n");

        return prompt.toString();
    }

    private List<String> extractCuesFromTheAnswer(String answer) {
        List<String> tips = new ArrayList<>();

        String[] lines = answer.split("\n");
        for (String line : lines) {
            String cleanline = line.trim();
            if (cleanline.startsWith("•") ||
                    cleanline.startsWith("-") ||
                    cleanline.matches("^\\d+\\.\\s.*") ||
                    cleanline.matches("^\\d+\\)\\s.*")) {
                tips.add(cleanline);
            }
        }

        if (tips.isEmpty() && answer.length() > 100) {
            String[] paragraphs = answer.split("\n\n");
            for (String paragraph : paragraphs) {
                if (paragraph.trim().length() > 30) {
                    tips.add(paragraph.trim());
                }
            }
            tips = tips.stream().limit(5).collect(Collectors.toList());
        }

        return tips;
    }

    private List<Long> extractSimilarIds(String response) {
        List<Long> ids = new ArrayList<>();

        if (response == null || response.isBlank() ||
                response.equalsIgnoreCase("NENHUM") ||
                response.contains("nenhum")) {
            return ids;
        }

        String clean = response.replaceAll("\\s+", "");
        String[] parts = clean.split(",");

        for (String part : parts) {
            try {
                Long id = Long.parseLong(part.trim());
                ids.add(id);
            } catch (NumberFormatException e) {
            }
        }

        return ids;
    }

    private String truncateText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text != null ? text : "";
        }
        return text.substring(0, maxLength) + "...";
    }

    public String generateResolutionTips(List<String> solutions, String currentProblem) {
        List<String> tipsList = generateResolutionTipsList(solutions, currentProblem);
        return String.join("\n\n", tipsList);
    }
}