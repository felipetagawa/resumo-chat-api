package com.soften.support.gemini_resumo.controller;
import com.soften.support.gemini_resumo.dto.*;
import com.soften.support.gemini_resumo.service.*;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/gemini")
@CrossOrigin(origins="*")
public class ProblemLineController {
    // A queue label: fits the ~286px SZ queue column beside the AnyDesk code.
    static final int LIMIT = 32;
    private final GeminiService gemini;
    private final SmartReplyRateLimiter limiter;
    public ProblemLineController(GeminiService gemini, SmartReplyRateLimiter limiter) {
        this.gemini = gemini; this.limiter = limiter;
    }
    @PostMapping(value="/linha-problema", consumes=MediaType.APPLICATION_JSON_VALUE, produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> line(@Valid @RequestBody ProblemLineRequest request) {
        if (!limiter.tryAcquire()) return ResponseEntity.status(429).header("Retry-After","60")
                .body(Map.of("erro","Limite atingido. Tente novamente em um minuto."));
        try { return ResponseEntity.ok(new ProblemLineResponse(normalize(gemini.generateProblemLine(request.mensagens().trim())))); }
        catch (GeminiIntegrationException e) {
            return ResponseEntity.status(e.getHttpStatus()).body(Map.of("erro","Não foi possível resumir o problema."));
        }
    }
    static String normalize(String raw) {
        String line = raw == null ? "" : raw.replaceAll("\\s+", " ").trim()
                .replaceAll("^[\"'“”]+|[\"'“”]+$", "").replaceAll("\\.+$", "").trim();
        if (line.equalsIgnoreCase("SEM_PROBLEMA")) return "";
        if (line.length() <= LIMIT) return line;
        String cut = line.substring(0, LIMIT - 1);
        // Prefer a word boundary, unless it would throw away more than half the label.
        int space = cut.lastIndexOf(' ');
        if (space >= LIMIT / 2) cut = cut.substring(0, space);
        return cut.trim() + "…";
    }
}
