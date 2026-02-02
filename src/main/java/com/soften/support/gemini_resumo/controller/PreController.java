package com.soften.support.gemini_resumo.controller;

import com.soften.support.gemini_resumo.models.dtos.PreTimeDto;
import com.soften.support.gemini_resumo.service.PreControlService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("api/pre-controls")
public class PreController {

    private final PreControlService preControlService;

    public PreController(PreControlService preControlService) {
        this.preControlService = preControlService;
    }

    @PostMapping
    public ResponseEntity<PreTimeDto> create(@RequestBody @Valid PreTimeDto dto) {
        preControlService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping
    public ResponseEntity<List<PreTimeDto>> get(
            @RequestParam(required = false) String name,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd") Date date
    ) {
        boolean hasName = name != null && !name.trim().isBlank();
        boolean hasDate = date != null;

        if (!hasName && !hasDate) {
            return ResponseEntity.ok(preControlService.List());
        }

        if (hasName && !hasDate) {
            return ResponseEntity.ok(preControlService.FindByName(name.trim()));
        }

        if (!hasName) {
            return ResponseEntity.ok(preControlService.FindByDate(date));
        }

        return ResponseEntity.ok(preControlService.FindByNameAndDate(name.trim(), date));
    }
}
