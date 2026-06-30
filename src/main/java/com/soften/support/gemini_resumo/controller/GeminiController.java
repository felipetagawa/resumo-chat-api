package com.soften.support.gemini_resumo.controller;

import com.soften.support.gemini_resumo.service.CalledService;
import com.soften.support.gemini_resumo.service.GeminiService;
import com.soften.support.gemini_resumo.service.GeminiIntegrationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/gemini")
@CrossOrigin(origins = "*")
public class GeminiController {

    private final GeminiService geminiService;
    private final CalledService calledService;

    public GeminiController(GeminiService geminiService, CalledService calledService) {
        this.geminiService = geminiService;
        this.calledService = calledService;
    }

    @PostMapping(value = "/resumir", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> resumirJson(@RequestBody Map<String, Object> body) {
        Object textoObj = body.get("texto");
        if (textoObj == null) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("erro", "Campo 'texto' é obrigatório no body JSON."));
        }
        String texto = textoObj.toString().trim();
        if (texto.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("erro", "Campo 'texto' não pode estar vazio."));
        }

        String promptComplement = resolvePromptComplement(body);

        try {
            String normalizedPromptComplement = geminiService.validateAndNormalizePromptComplement(promptComplement);
            String summary = geminiService.generateSummary(texto, normalizedPromptComplement);
            calledService.SaveCall(summary);

            return ResponseEntity.ok(Map.of("summary", summary));
        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("erro", e.getMessage()));
        } catch (GeminiIntegrationException e) {
            return ResponseEntity
                    .status(e.getHttpStatus())
                    .body(Map.of("erro", e.getClientMessage()));
        }
    }

    @PostMapping(value = "/resumir", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> resumirPlainText(@RequestBody String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("erro", "Body não pode estar vazio."));
        }
        try {
            String resumo = geminiService.generateSummary(texto.trim());
            calledService.SaveCall(resumo);

            return ResponseEntity.ok(Map.of("summary", resumo));
        } catch (GeminiIntegrationException e) {
            return ResponseEntity
                    .status(e.getHttpStatus())
                    .body(Map.of("erro", e.getClientMessage()));
        }
    }

    @GetMapping("/ping")
    public ResponseEntity<?> ping() {
        return ResponseEntity.ok(Map.of("status", "ok", "app", "gemini-summary"));
    }

    private String resolvePromptComplement(Map<String, Object> body) {
        Object promptComplementObj = body.get("promptComplement");
        if (promptComplementObj != null) {
            String value = promptComplementObj.toString().trim();
            if (!value.isEmpty()) {
                return value;
            }
        }

        Object complementoObj = body.get("complemento");
        if (complementoObj != null) {
            String value = complementoObj.toString().trim();
            if (!value.isEmpty()) {
                return value;
            }
        }

        return null;
    }

}
