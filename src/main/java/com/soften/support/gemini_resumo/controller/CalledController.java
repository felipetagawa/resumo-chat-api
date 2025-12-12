package com.soften.support.gemini_resumo.controller;

import com.soften.support.gemini_resumo.models.dtos.*;
import com.soften.support.gemini_resumo.models.entities.CalledEntity;
import com.soften.support.gemini_resumo.service.CalledService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chamado")
@CrossOrigin(origins = "*")
public class CalledController {

    private final CalledService calledService;

    public CalledController(CalledService calledService) {
        this.calledService = calledService;
    }

    @PostMapping("/processar-dica")
    public ResponseEntity<TipResponseDto> processTip(@RequestBody TextCalledDto dto) {
        TipResponseDto tip = calledService.processFullTip(dto.texto());
        return ResponseEntity.ok(tip);
    }

    @PostMapping("/salvar-resumo")
    public ResponseEntity<CalledEntity> saveSummary(@RequestBody TextCalledDto dto) {
        CalledEntity chamado = calledService.SaveCall(dto.texto());
        return ResponseEntity.ok(chamado);
    }
}