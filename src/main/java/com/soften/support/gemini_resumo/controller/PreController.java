package com.soften.support.gemini_resumo.controller;

import com.soften.support.gemini_resumo.models.dtos.PreTimeDto;
import com.soften.support.gemini_resumo.service.PreControlService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/pre-controls")
public class PreController {

    private final PreControlService service;

    public PreController(PreControlService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PreTimeDto> create(@RequestBody @Valid PreTimeDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @GetMapping
    public ResponseEntity<Page<PreTimeDto>> getAll(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Boolean negociation,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,

            @PageableDefault(page = 0, size = 15, sort = "date", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<PreTimeDto> page = service.findAll(name, negociation, date, dateFrom, dateTo, pageable);
        return ResponseEntity.ok(page);
    }
}