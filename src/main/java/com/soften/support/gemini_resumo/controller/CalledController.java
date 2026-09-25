package com.soften.support.gemini_resumo.controller;

import com.soften.support.gemini_resumo.models.dtos.*;
import com.soften.support.gemini_resumo.service.CalledService;
import com.soften.support.gemini_resumo.service.GeminiIntegrationException;
import com.soften.support.gemini_resumo.service.GeminiService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chamado")
@CrossOrigin(origins = "*")
public class CalledController {

    private final CalledService calledService;
    private final GeminiService geminiService;

    public CalledController(CalledService calledService, GeminiService geminiService) {
        this.calledService = calledService;
        this.geminiService = geminiService;
    }

    @PostMapping("/processar-dica")
    public ResponseEntity<?> processTip(@RequestBody TextCalledDto dto) {
        try {
            String promptComplement = geminiService.validateAndNormalizePromptComplement(dto.promptComplement());
            TipResponseDto tip = calledService.processFullTip(dto.texto(), promptComplement);
            return ResponseEntity.ok(tip);
        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("erro", e.getMessage()));
        } catch (GeminiIntegrationException e) {
            return ResponseEntity.status(e.getHttpStatus()).body(Map.of("erro", e.getClientMessage()));
        }
    }

    @PostMapping("/salvar-resumo")
    public ResponseEntity<?> saveSummary(@RequestBody TextCalledDto dto) {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(Map.of("erro", "Persistência de resumos desativada."));
    }
}
