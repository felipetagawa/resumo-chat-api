package com.soften.support.gemini_resumo.models.dtos;

import java.time.LocalDate;
import java.util.UUID;

public record PreTimeDto(
        UUID id,
        String name,
        String nameClient,
        LocalDate date,
        String time,
        boolean negociation
) {}
