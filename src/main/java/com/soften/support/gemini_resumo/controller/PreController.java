package com.soften.support.gemini_resumo.controller;

import com.soften.support.gemini_resumo.models.dtos.PreTimeDto;
import com.soften.support.gemini_resumo.service.PreControlService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/pre-controls")
public class PreController {

    private final PreControlService preControlService;

    public PreController(PreControlService preControlService) {
        this.preControlService = preControlService;
    }

    @PostMapping
    public ResponseEntity<PreTimeDto> create(@RequestBody @Valid PreTimeDto dto) {
        PreTimeDto created = preControlService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<PreTimeDto>> get(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Boolean negociation,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        boolean hasName = name != null && !name.trim().isBlank();
        boolean hasNegociation = negociation != null;

        String safeName = hasName ? name.trim() : null;

        boolean hasFrom = dateFrom != null;
        boolean hasTo = dateTo != null;

        if (hasFrom || hasTo) {
            LocalDate from = hasFrom ? dateFrom : dateTo;
            LocalDate to   = hasTo ? dateTo : dateFrom;

            if (hasName && hasNegociation) {
                return ResponseEntity.ok(preControlService.findByNameAndPeriodAndNegociation(safeName, from, to, negociation));
            }
            if (hasName) {
                return ResponseEntity.ok(preControlService.findByNameAndPeriod(safeName, from, to));
            }
            if (hasNegociation) {
                return ResponseEntity.ok(preControlService.findByPeriodAndNegociation(from, to, negociation));
            }
            return ResponseEntity.ok(preControlService.findByPeriod(from, to));
        }

        if (date != null) {
            if (hasName && hasNegociation) {
                return ResponseEntity.ok(preControlService.FindByNameAndDateAndNegociation(safeName, date, negociation));
            }
            if (hasName) {
                return ResponseEntity.ok(preControlService.FindByNameAndDate(safeName, date));
            }
            if (hasNegociation) {
                return ResponseEntity.ok(preControlService.FindByDateAndNegociation(date, negociation));
            }
            return ResponseEntity.ok(preControlService.FindByDate(date));
        }

        if (hasName && hasNegociation) {
            return ResponseEntity.ok(preControlService.FindByNameAndNegociation(safeName, negociation));
        }
        if (hasName) {
            return ResponseEntity.ok(preControlService.FindByName(safeName));
        }

        if (hasNegociation) {
            return ResponseEntity.ok(preControlService.FindByNegociation(negociation));
        }

        return ResponseEntity.ok(preControlService.List());
    }
}