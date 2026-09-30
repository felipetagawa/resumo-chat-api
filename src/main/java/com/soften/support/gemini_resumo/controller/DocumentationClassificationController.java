package com.soften.support.gemini_resumo.controller;

import com.soften.support.gemini_resumo.models.dtos.DocumentationClassificationRequest;
import com.soften.support.gemini_resumo.service.ClassificationRateLimiter;
import com.soften.support.gemini_resumo.service.JevDocumentationClassificationService;
import com.soften.support.gemini_resumo.service.JevIntegrationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/classification")
public class DocumentationClassificationController {

    private final JevDocumentationClassificationService service;
    private final ClassificationRateLimiter rateLimiter;

    public DocumentationClassificationController(
            JevDocumentationClassificationService service,
            ClassificationRateLimiter rateLimiter
    ) {
        this.service = service;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping(
            value = "/documentation",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> classifyDocumentation(
            @RequestBody(required = false) DocumentationClassificationRequest request
    ) {
        if (request == null) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("erro", "Body é obrigatório."));
        }

        if (!rateLimiter.tryAcquire()) {
            return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", "60")
                    .body(Map.of("erro", "Muitas classificações em pouco tempo. Tente novamente em instantes."));
        }

        try {
            return ResponseEntity.ok(service.classify(request.context(), request.candidates()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("erro", e.getMessage()));
        } catch (JevIntegrationException e) {
            return ResponseEntity
                    .status(e.getHttpStatus())
                    .body(Map.of("erro", e.getClientMessage()));
        }
    }
}
