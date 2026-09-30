package com.soften.support.gemini_resumo.controller;

import com.soften.support.gemini_resumo.models.dtos.ProductClassificationRequest;
import com.soften.support.gemini_resumo.models.dtos.ProductClassificationResponse;
import com.soften.support.gemini_resumo.service.JevIntegrationException;
import com.soften.support.gemini_resumo.service.JevProductClassificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/classification")
@CrossOrigin(origins = "*")
public class ProductClassificationController {

    private final JevProductClassificationService classificationService;

    public ProductClassificationController(JevProductClassificationService classificationService) {
        this.classificationService = classificationService;
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

        try {
            ProductClassificationResponse response = classificationService.classify(request.conversation());
            return ResponseEntity.ok(response);
        } catch (JevIntegrationException e) {
            return ResponseEntity
                    .status(e.getHttpStatus())
                    .body(Map.of("erro", e.getClientMessage()));
        }
    }
}
