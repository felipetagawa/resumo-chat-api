package com.soften.support.gemini_resumo.service;

import com.soften.support.gemini_resumo.docs.ClaudeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/** Queue label: Claude first when configured; Gemini answers when it is absent, slow or failing. */
@Service
public class ProblemLineService {

    // Thinking is off, so this is all visible text: the label is ~10 tokens, plus room for a preamble or a
    // line of explanation the controller drops. A reply cut here goes to Gemini. The controller cuts it to 32 chars.
    static final long CLAUDE_MAX_TOKENS = 128;
    // The extension aborts at 10s: one Claude attempt, then one Gemini attempt (connect + read), must fit.
    static final Duration CLAUDE_TIMEOUT = Duration.ofSeconds(3);

    private final GeminiService gemini;
    private final ClaudeService claude;

    public ProblemLineService(GeminiService gemini) { this(gemini, null); }

    @Autowired
    public ProblemLineService(GeminiService gemini, ClaudeService claude) {
        this.gemini = gemini;
        this.claude = claude;
    }

    public String line(String messages) {
        if (claude == null || !claude.isConfigured()) return gemini.generateProblemLine(messages);
        return claude.generateOnceWithoutThinking(GeminiService.PROBLEM_LINE_POLICY,
                        List.of(new ClaudeService.Turn(true, "MENSAGENS DO CLIENTE:\n" + messages)),
                        CLAUDE_MAX_TOKENS, CLAUDE_TIMEOUT, "problem_line")
                // Claude already spent up to CLAUDE_TIMEOUT: a second Gemini attempt would pass the 10s abort.
                .orElseGet(() -> gemini.generateProblemLine(messages, 1));
    }
}
