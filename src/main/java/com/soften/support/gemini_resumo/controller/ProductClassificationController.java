package com.soften.support.gemini_resumo.controller;

import com.soften.support.gemini_resumo.config.TypeSafeApiProperties;
import com.soften.support.gemini_resumo.models.dtos.ProductClassificationRequest;
import com.soften.support.gemini_resumo.models.dtos.ProductClassificationResponse;
import com.soften.support.gemini_resumo.service.ClassificationRateLimiter;
import com.soften.support.gemini_resumo.service.JevIntegrationException;
import com.soften.support.gemini_resumo.service.JevProductClassificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/classification")
public class ProductClassificationController {

    private final JevProductClassificationService classificationService;
    private final ClassificationRateLimiter rateLimiter;
    private final TypeSafeApiProperties properties;

    public ProductClassificationController(
            JevProductClassificationService classificationService,
            ClassificationRateLimiter rateLimiter,
            TypeSafeApiProperties properties
    ) {
        this.classificationService = classificationService;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    @PostMapping(
            value = "/product",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> classifyProduct(@RequestBody(required = false) ProductClassificationRequest request) {
        if (request == null || request.conversation() == null || request.conversation().isBlank()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("erro", "Campo 'conversation' é obrigatório e não pode estar vazio."));
        }

        String conversation = request.conversation().trim();
        if (conversation.length() > properties.getSafeMaxConversationChars()) {
            return ResponseEntity
                    .status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(Map.of(
                            "erro",
                            "A conversa excede o limite de " + properties.getSafeMaxConversationChars() + " caracteres."
                    ));
        }

        if (!rateLimiter.tryAcquire()) {
            return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", "60")
                    .body(Map.of("erro", "Muitas classificações em pouco tempo. Tente novamente em instantes."));
        }

        try {
            ProductClassificationResponse response = classificationService.classify(conversation);
            return ResponseEntity.ok(response);
        } catch (JevIntegrationException e) {
            return ResponseEntity
                    .status(e.getHttpStatus())
                    .body(Map.of("erro", e.getClientMessage()));
        }
    }
}
