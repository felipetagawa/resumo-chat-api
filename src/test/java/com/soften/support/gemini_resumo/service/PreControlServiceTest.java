package com.soften.support.gemini_resumo.service;

import com.soften.support.gemini_resumo.models.dtos.PreTimeDto;
import com.soften.support.gemini_resumo.repositorys.PreControlRepositoy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PreControlServiceTest {

    @Mock
    private PreControlRepositoy repo;

    @Test
    void createShouldRejectWithGoneAndNeverSave() {
        PreControlService service = new PreControlService(repo);
        PreTimeDto request = new PreTimeDto(null, "John", "Client", null, null, false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.create(request));

        assertEquals(HttpStatus.GONE, ex.getStatusCode());
        assertEquals("PRE profile persistence is disabled", ex.getReason());
        verifyNoInteractions(repo);
    }
}
