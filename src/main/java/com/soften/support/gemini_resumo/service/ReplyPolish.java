package com.soften.support.gemini_resumo.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic safety net: strips stock openers/closers the prompt forbids. Never rewrites content. */
final class ReplyPolish {
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final Pattern OPENER = Pattern.compile(
            "^\\s*(?:(?:entendo|compreendo)(?:\\s+perfeitamente)?"
            + "(?:\\s+(?:sua|a|o\\s+seu|seu)\\s+(?:situa[çc][ãa]o|frustra[çc][ãa]o|preocupa[çc][ãa]o|d[úu]vida|problema|lado))?\\s*[.!,…]+"
            + "|(?:certo|perfeito|claro)\\s*[!.,]+"
            + "|prezad[oa]s?(?:\\(a\\))?[^,.!?\\n]{0,40}[,.!?])\\s*", FLAGS);
    private static final Pattern CLOSER = Pattern.compile(
            "(?:(?:fico|estou|sigo|seguimos|ficamos|estamos)\\s+(?:à|a)\\s+disposi[çc][ãa]o"
            + "|qualquer\\s+(?:outra\\s+)?d[úu]vida"
            + "|espero\\s+ter\\s+ajudado"
            + "|agrade[çc]o\\s+(?:pela|a)\\s+(?:sua\\s+)?(?:paci[êe]ncia|compreens[ãa]o)"
            + "|agrade[çc]o\\s+(?:o|pelo)\\s+(?:seu\\s+)?contato"
            + "|pe[çc]o\\s+desculpas\\s+pelo\\s+transtorno).*", FLAGS | Pattern.DOTALL);

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
        return !last.isEmpty() && CLOSER.matcher(last).matches() ? text.substring(0, cut).strip() : text;
    }

    private static String capitalize(String text) {
        if (text.isEmpty()) return text;
        int cp = text.codePointAt(0);
        return new StringBuilder().appendCodePoint(Character.toUpperCase(cp))
                .append(text.substring(Character.charCount(cp))).toString();
    }
}
