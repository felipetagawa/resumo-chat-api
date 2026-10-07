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
public class SmartReplyController {
    private final SmartReplyService service;
    private final SmartReplyRateLimiter limiter;
    public SmartReplyController(SmartReplyService service, SmartReplyRateLimiter limiter) {
        this.service=service; this.limiter=limiter;
    }
    @PostMapping(value="/responder", consumes=MediaType.APPLICATION_JSON_VALUE, produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> reply(@Valid @RequestBody SmartReplyRequest request) {
        if (!limiter.tryAcquire()) return ResponseEntity.status(429).header("Retry-After","60")
                .body(Map.of("erro","Limite de sugestões atingido. Tente novamente em um minuto."));
        try { return ResponseEntity.ok(service.reply(request)); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("erro","Contexto ou perfil inválido.")); }
        catch (GeminiIntegrationException e) {
            return ResponseEntity.status(e.getHttpStatus()).body(Map.of("erro","Não foi possível sugerir uma resposta. Tente novamente."));
        }
    }
}
