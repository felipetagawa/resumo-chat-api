package com.soften.support.gemini_resumo.docs;

import com.fasterxml.jackson.databind.JsonNode;
import com.soften.support.gemini_resumo.service.GeminiIntegrationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/docs")
public class DocsChatController {

    private static final Set<String> PRODUCTS = Set.of("Soften SIEM", "GerencieAqui", "SoftenFood");

    private final DocsChatService service;
    private final DocsIndex index;
    private final int limit;
    private long windowStart;
    private int used;

    public DocsChatController(DocsChatService service, DocsIndex index,
                              @Value("${docs-chat.rate-limit-per-minute:30}") int limit) {
        this.service = service;
        this.index = index;
        this.limit = Math.max(1, limit);
    }

    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> chat(@RequestBody(required = false) JsonNode body) {
        List<ClaudeService.Turn> history;
        String product;
        try {
            history = turns(body == null ? null : body.get("messages"));
            JsonNode p = body.get("product");
            product = p == null || p.isNull() ? null : p.asText();
            if (product != null && !PRODUCTS.contains(product)) throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", "Conversa inválida."));
        }
        if (!tryAcquire()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "60")
                    .body(Map.of("erro", "Muitas consultas em pouco tempo. Tente novamente em um minuto."));
        }
        try {
            return ResponseEntity.ok(service.chat(history, product));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        } catch (GeminiIntegrationException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("erro", "Não foi possível consultar a documentação agora. Tente novamente."));
        }
    }

    @GetMapping("/index-info")
    public Map<String, Object> indexInfo() {
        return Map.of("source", "https://docs.softensistemas.com.br", "passages", index.size(), "builtAt", index.builtAt());
    }

    private static List<ClaudeService.Turn> turns(JsonNode messages) {
        if (messages == null || !messages.isArray()) throw new IllegalArgumentException();
        List<ClaudeService.Turn> out = new ArrayList<>();
        for (JsonNode m : messages) {
            String role = m.path("role").asText("");
            JsonNode content = m.get("content");
            if (!("user".equals(role) || "assistant".equals(role)) || content == null || !content.isTextual())
                throw new IllegalArgumentException();
            out.add(new ClaudeService.Turn("user".equals(role), content.textValue().trim()));
        }
        return out;
    }

    private synchronized boolean tryAcquire() {
        long now = System.currentTimeMillis();
        if (now - windowStart >= 60_000) { windowStart = now; used = 0; }
        if (used >= limit) return false;
        used++;
        return true;
    }
}
