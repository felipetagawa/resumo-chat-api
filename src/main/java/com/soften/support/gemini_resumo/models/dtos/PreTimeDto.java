package com.soften.support.gemini_resumo.models.dtos;

import java.time.LocalDateTime;
import java.util.UUID;

public record PreTimeDto(
        UUID id,
        String name,
        String nameClient,
        LocalDateTime date,
        String time,
        boolean negociation
) {}
