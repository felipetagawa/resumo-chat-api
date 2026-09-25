package com.soften.support.gemini_resumo.service;

import com.soften.support.gemini_resumo.models.dtos.PreTimeDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Service
public class PreControlService {
    public PreTimeDto create(PreTimeDto dto) {
        throw new ResponseStatusException(HttpStatus.GONE, "PRE profile persistence is disabled");
    }

    public Page<PreTimeDto> findAll(String name, Boolean negociation, LocalDate dateExact,
                                    LocalDate dateFrom, LocalDate dateTo, Pageable pageable) {
        return new PageImpl<>(List.of(), pageable, 0);
    }
}
