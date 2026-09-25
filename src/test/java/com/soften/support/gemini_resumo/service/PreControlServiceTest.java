package com.soften.support.gemini_resumo.service;

import com.soften.support.gemini_resumo.models.dtos.PreTimeDto;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class PreControlServiceTest {
    private final PreControlService service = new PreControlService();

    @Test
    void createRemainsGoneWithoutPersistence() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.create(new PreTimeDto(null, "John", "Client", null, null, false)));
        assertEquals(HttpStatus.GONE, ex.getStatusCode());
    }

    @Test
    void listReturnsEmptyCompatiblePage() {
        var page = service.findAll(null, null, null, null, null, PageRequest.of(1, 15));
        assertTrue(page.isEmpty());
        assertEquals(0, page.getTotalElements());
        assertEquals(1, page.getNumber());
    }
}
