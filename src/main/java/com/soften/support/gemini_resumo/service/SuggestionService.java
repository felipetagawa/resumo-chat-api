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

    public List<CalledEntity> filterCallsBySimilarity(List<CalledEntity> moduleCalls, String currentProblem) {
        if (moduleCalls == null || moduleCalls.isEmpty() ||
                currentProblem == null || currentProblem.isBlank()) {
            return Collections.emptyList();
        }

        System.out.println("=== FILTERING CALLS BY SIMILARITY ===");
        System.out.println("Total calls in module: " + moduleCalls.size());
        System.out.println("Current problem: " + currentProblem);

        System.out.println("=== SAMPLE OF CALLS IN MODULE ===");
        for (int i = 0; i < Math.min(3, moduleCalls.size()); i++) {
            CalledEntity call = moduleCalls.get(i);
            System.out.println("Sample call " + (i+1) + " - ID: " + call.getId());
            System.out.println("  Problem: " + call.getProblem());
            System.out.println("  Has solution: " + (call.getSolution() != null && !call.getSolution().isBlank()));
            System.out.println("---");
        }

        try {
            System.out.println("Analyzing ALL " + moduleCalls.size() + " calls in the module");

            String prompt = createPromptForSimilarityAnalysis(moduleCalls, currentProblem);

            String response = geminiService.ask(prompt);
            System.out.println("Gemini response (similarity): '" + response + "'");

            List<UUID> similarIds = extractSimilarUUIDs(response);
            System.out.println("Similar UUIDs found: " + similarIds);

            List<CalledEntity> similarCalls = moduleCalls.stream()
                    .filter(c -> c.getId() != null && similarIds.contains(c.getId()))
                    .collect(Collectors.toList());

            System.out.println("Similar calls filtered: " + similarCalls.size());

            if (similarCalls.isEmpty()) {
                System.out.println("No similar calls found by Gemini");
            }

            return similarCalls;

        } catch (Exception e) {
            System.err.println("Error in similarity filtering: " + e.getMessage());
            e.printStackTrace();

            return Collections.emptyList();
        }
    }

    private String createPromptForSimilarityAnalysis(List<CalledEntity> calls, String currentProblem) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Você é um analista de suporte técnico especializado. Sua tarefa é identificar problemas históricos que são SIMILARES ao problema atual.\n\n");

        prompt.append("**PROBLEMA ATUAL (analise com atenção):**\n");
        prompt.append("\"\"\"\n").append(currentProblem).append("\n\"\"\"\n\n");

        prompt.append("**PROBLEMAS HISTÓRICOS NESTE MÓDULO:**\n");
        prompt.append("(Total: ").append(calls.size()).append(" chamados)\n\n");

        int count = 0;
        for (CalledEntity call : calls) {
            count++;
            prompt.append("--- CHAMADO #").append(count).append(" ---\n");
            prompt.append("ID EXATO (UUID): ").append(call.getId().toString()).append("\n");
            prompt.append("PROBLEMA: ").append(truncateText(call.getProblem(), 300)).append("\n");
            if (call.getSolution() != null && !call.getSolution().isBlank()) {
                prompt.append("SOLUÇÃO: ").append(truncateText(call.getSolution(), 150)).append("\n");
            }
            prompt.append("\n");
        }

        prompt.append("\n **INSTRUÇÕES CRÍTICAS:**\n");
        prompt.append("1. Use APENAS os IDs UUID listados acima (não invente IDs)\n");
        prompt.append("2. Foque na ESSÊNCIA do problema atual: 'não consegue inserir o valor'\n");
        prompt.append("3. Considere problemas similares sobre: inserção de valores, campos, preenchimento, valores monetários\n");
        prompt.append("4. Ignore problemas sobre NCM, códigos, digitação - foco em VALORES\n");
        prompt.append("5. Selecione IDs de problemas com sintomas similares (máximo 3)\n\n");

        prompt.append("**FORMATO DA RESPOSTA (OBRIGATÓRIO):**\n");
        prompt.append("APENAS UUIDs exatos separados por vírgula\n");
        prompt.append("Exemplo: 45caef16-2937-4f29-a4b8-73dc487024c7, f5671055-c8d1-4eaf-8521-a8df4ff8bd6d\n");
        prompt.append("Se NENHUM for similar: 0\n\n");

        prompt.append("**IDs SIMILARES (use apenas UUIDs listados acima):**\n");

        return prompt.toString();
    }

    private List<UUID> extractSimilarUUIDs(String response) {
        List<UUID> uuids = new ArrayList<>();

        if (response == null || response.isBlank()) {
            return uuids;
        }

        String clean = response.trim();

        clean = clean.replaceAll("^(IDs? (similares|encontrados|selecionados):\\s*)", "");
        clean = clean.replaceAll("^(Sua análise:\\s*)", "");
        clean = clean.replaceAll("^(Resposta:\\s*)", "");
        clean = clean.replaceAll("^(UUIDs similares:\\s*)", "");

        System.out.println("Cleaned response for UUID extraction: '" + clean + "'");

        if (clean.equalsIgnoreCase("0") ||
                clean.equalsIgnoreCase("nenhum") ||
                clean.equalsIgnoreCase("não") ||
                clean.equalsIgnoreCase("nao") ||
                clean.contains("nenhum similar")) {
            return uuids;
        }

        String[] parts = clean.split("[,;\\s]+");

        for (String part : parts) {
            String trimmed = part.trim();

            trimmed = trimmed.replaceAll("\\.$", "");

            try {
                if (isValidUUIDFormat(trimmed)) {
                    UUID uuid = UUID.fromString(trimmed);
                    if (!uuids.contains(uuid)) {
                        uuids.add(uuid);
                    }
                }
            } catch (IllegalArgumentException e) {
                System.out.println("Parte inválida para UUID: '" + trimmed + "' - " + e.getMessage());
            }
        }

        if (uuids.size() > 5) {
            uuids = uuids.subList(0, 5);
        }

        System.out.println("Extracted UUIDs: " + uuids);
        return uuids;
    }

    private boolean isValidUUIDFormat(String str) {
        if (str == null) return false;

        String uuidPattern = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
        return str.matches(uuidPattern);
    }

    private String createPromptForTipsGeneration(List<String> solutions, String currentProblem) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Você é um analista técnico sênior. Com base nas soluções aplicadas a problemas similares, gere dicas práticas para resolver o problema atual.\n\n");

        prompt.append("**PROBLEMA ATUAL PARA RESOLVER:**\n");
        prompt.append("\"\"\"\n").append(currentProblem).append("\n\"\"\"\n\n");

        prompt.append("**SOLUÇÕES APLICADAS EM CASOS SIMILARES:**\n");
        prompt.append("(Total: ").append(solutions.size()).append(" soluções)\n\n");

        for (int i = 0; i < Math.min(solutions.size(), 10); i++) {
            prompt.append("--- SOLUÇÃO ").append(i + 1).append(" ---\n");
            prompt.append(truncateText(solutions.get(i), 500)).append("\n\n");
        }

        if (solutions.size() > 10) {
            prompt.append("... e mais ").append(solutions.size() - 10).append(" soluções\n\n");
        }

        prompt.append("\n**INSTRUÇÕES PARA AS DICAS:**\n");
        prompt.append("1. Extraia PADRÕES COMUNS das soluções\n");
        prompt.append("2. Crie dicas PRÁTICAS e EXECUTÁVEIS\n");
        prompt.append("3. Foque em etapas de solução de problemas sobre INSERÇÃO DE VALORES\n");
        prompt.append("4. Inclua verificações de configuração se relevantes\n");
        prompt.append("5. Priorize as soluções mais eficazes\n");
        prompt.append("6. Gere 3-6 dicas no máximo\n\n");

        prompt.append("**FORMATO DA RESPOSTA (em português):**\n");
        prompt.append("- Cada dica em uma nova linha começando com • (ponto de lista)\n");
        prompt.append("- Dicas devem ser em português\n");
        prompt.append("- Seja claro e conciso\n");
        prompt.append("- Exemplo:\n");
        prompt.append("• Verifique se o campo de valor está habilitado\n");
        prompt.append("• Confirme as permissões do usuário\n");
        prompt.append("• Reinicie o módulo fiscal\n\n");

        prompt.append("**SUAS DICAS (em português):**\n");

        return prompt.toString();
    }

    private List<String> extractTipsFromAnswer(String answer) {
        List<String> tips = new ArrayList<>();

        if (answer == null || answer.isBlank()) {
            return tips;
        }

        String[] lines = answer.split("\n");
        for (String line : lines) {
            String cleanLine = line.trim();
            if (cleanLine.startsWith("•") ||
                    cleanLine.startsWith("-") ||
                    cleanLine.startsWith("*") ||
                    cleanLine.matches("^\\d+\\.\\s.*") ||
                    cleanLine.matches("^\\d+\\)\\s.*")) {
                tips.add(cleanLine);
            }
        }

        if (tips.isEmpty() && answer.length() > 100) {
            String[] paragraphs = answer.split("\n\n");
            for (String paragraph : paragraphs) {
                String trimmed = paragraph.trim();
                if (trimmed.length() > 30 && !trimmed.contains("**") &&
                        !trimmed.contains("RESPOSTA") && !trimmed.contains("FORMATO")) {
                    if (!trimmed.startsWith("•")) {
                        trimmed = "• " + trimmed;
                    }
                    tips.add(trimmed);
                }
            }
            tips = tips.stream().limit(5).collect(Collectors.toList());
        }

        return tips;
    }

    private List<String> createFallbackTips(List<String> solutions) {
        List<String> tips = new ArrayList<>();

        tips.add("**ANÁLISE DE SOLUÇÕES HISTÓRICAS:**");
        tips.add("Baseado em " + solutions.size() + " casos similares encontrados:");

        for (int i = 0; i < Math.min(solutions.size(), 4); i++) {
            String solution = solutions.get(i);
            String summary = truncateText(solution, 180);
            tips.add((i + 1) + ". " + summary);
        }

        if (solutions.size() > 4) {
            tips.add("... e mais " + (solutions.size() - 4) + " soluções no banco de dados.");
        }

        return tips;
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    public List<String> generateResolutionTipsList(List<String> solutions, String currentProblem) {
        List<String> tips = new ArrayList<>();

        if (solutions == null || solutions.isEmpty()) {
            tips.add("Não foram encontradas soluções históricas para análise.");
            return tips;
        }

        try {
            System.out.println("=== GENERATING TIPS ===");
            System.out.println("Available solutions: " + solutions.size());
            System.out.println("Current problem: " + currentProblem.substring(0, Math.min(100, currentProblem.length())));

            String prompt = createPromptForTipsGeneration(solutions, currentProblem);

            String response = geminiService.ask(prompt);
            System.out.println("Gemini response (tips): " +
                    (response.length() > 200 ? response.substring(0, 200) + "..." : response));

            tips = extractTipsFromAnswer(response);

            if (tips.isEmpty()) {
                System.out.println("Gemini did not generate formatted tips. Using fallback...");
                tips = createFallbackTips(solutions);
            }

            if (!tips.isEmpty() && tips.size() > 0) {
                tips.add(0, "**DICAS GERADAS - IA:**");
            }

            System.out.println("Tips generated: " + tips.size());
            return tips;

        } catch (Exception e) {
            System.err.println("Error generating tips: " + e.getMessage());
            tips.add("Erro ao processar dicas automaticamente.");
            tips.add("Soluções encontradas no histórico: " + solutions.size());
            return tips;
        }
    }

    public String generateResolutionTips(List<String> solutions, String currentProblem) {
        List<String> tipsList = generateResolutionTipsList(solutions, currentProblem);
        return String.join("\n\n", tipsList);
    }
}