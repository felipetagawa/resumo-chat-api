package com.soften.support.gemini_resumo.models.dtos;

import java.util.Date;
import java.util.UUID;

public record PreTimeDto(UUID id, String name, String nameClient, Date date, String time) {
}
