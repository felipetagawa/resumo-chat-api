package com.soften.support.gemini_resumo.service;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic safety net: strips stock openers/closers the prompt forbids. Never rewrites content. */
final class ReplyPolish {
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final Pattern OPENER = Pattern.compile(
            "^\\s*(?:(?:entendo|compreendo)(?:\\s+perfeitamente)?"
            + "(?:\\s+(?:sua|a\\s+sua|a|o\\s+seu|seu)\\s+(?:situa[çc][ãa]o|frustra[çc][ãa]o|preocupa[çc][ãa]o|d[úu]vida|problema|lado)"
            + "(?:\\s+(?:com|pela|pelo|por|em|de)\\s+[^.!?,\\n]{1,40})?)?\\s*[.!,…]+"
            + "|(?:certo|perfeito|claro)\\s*[!.,]+"
            + "|pe[çc]o\\s+desculpas\\s+pelo\\s+transtorno\\s*[.!,…]+"
            + "|agrade[çc]o\\s+(?:(?:o|pelo)\\s+(?:seu\\s+)?contato|(?:pela|a)\\s+(?:sua\\s+)?(?:paci[êe]ncia|compreens[ãa]o))\\s*[.!,…]+"
            + "|prezad[oa]s?(?:\\(a\\))?[^,.!?\\n]{0,40}[,.!?])\\s*", FLAGS);
    // A closing sentence is removed only when what follows the stock phrase is filler too.
    private static final Pattern CLOSER = Pattern.compile(
            "(?:(?:fico|estou|sigo|seguimos|ficamos|estamos)\\s+(?:à|a)\\s+disposi[çc][ãa]o"
            + "|qualquer\\s+(?:outra\\s+)?d[úu]vida"
            + "|espero\\s+ter\\s+ajudado"
            + "|agrade[çc]o\\s+(?:pela|a)\\s+(?:sua\\s+)?(?:paci[êe]ncia|compreens[ãa]o)"
            + "|agrade[çc]o\\s+(?:o|pelo)\\s+(?:seu\\s+)?contato"
            + "|pe[çc]o\\s+desculpas\\s+pelo\\s+transtorno)", FLAGS);
    private static final Set<String> FILLER = Set.of("para", "caso", "qualquer", "outra", "outras", "dúvida", "duvida",
            "dúvidas", "duvidas", "que", "o", "a", "precisar", "preciso", "precise", "for", "tenha", "houver", "surgir",
            "mais", "alguma", "algum", "coisa", "ajudar", "ajudá", "lo", "la", "é", "só", "so", "chamar", "chame", "chama",
            "me", "avisar", "avisa", "avise", "falar", "fala", "fale", "estou", "fico", "à", "disposição", "disposicao",
            "sempre", "conte", "comigo", "com", "nós", "nos", "momento", "e", "ou", "de", "da", "do", "em", "aqui", "aí",
            "ai", "suporte", "equipe", "nosso", "pessoal", "vocês", "você", "voce", "sua", "seu", "ajuda", "necessário",
            "necessario", "esclarecer", "esclarecimentos", "esclarecimento", "sobre", "atendimento", "contato", "paciência",
            "paciencia", "compreensão", "compreensao", "ajudado", "ter", "espero");

    private ReplyPolish() {}

    static String apply(String raw) {
        String text = raw == null ? "" : raw.strip();
        for (int i = 0; i < 3; i++) {
            Matcher m = OPENER.matcher(text);
            if (!m.lookingAt() || m.end() == 0) break;
            text = capitalize(text.substring(m.end()).strip());
        }
        for (int i = 0; i < 3; i++) {
            String next = dropClosingSentence(text);
            if (next.equals(text)) break;
            text = next;
        }
        return text.strip();
    }

    private static String dropClosingSentence(String text) {
        int end = text.length();
        while (end > 0) {
            int cp = text.codePointBefore(end);
            if (!Character.isWhitespace(cp) && ".!?…".indexOf(cp) < 0 && Character.getType(cp) != Character.OTHER_SYMBOL) break;
            end -= Character.charCount(cp);
        }
        int cut = 0;
        for (int i = end - 1; i >= 0; i--) {
            if (".!?…\n".indexOf(text.charAt(i)) >= 0) { cut = i + 1; break; }
        }
        String last = text.substring(cut).strip();
        return isClosingSentence(last) ? text.substring(0, cut).strip() : text;
    }

    private static boolean isClosingSentence(String sentence) {
        Matcher m = CLOSER.matcher(sentence);
        if (sentence.isEmpty() || !m.lookingAt()) return false;
        for (String word : sentence.substring(m.end()).toLowerCase(java.util.Locale.ROOT).split("[^\\p{L}]+"))
            if (!word.isEmpty() && !FILLER.contains(word)) return false;
        return true;
    }

    private static String capitalize(String text) {
        if (text.isEmpty()) return text;
        int cp = text.codePointAt(0);
        return new StringBuilder().appendCodePoint(Character.toUpperCase(cp))
                .append(text.substring(Character.charCount(cp))).toString();
    }
}
