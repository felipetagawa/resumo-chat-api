package com.soften.support.gemini_resumo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soften.support.gemini_resumo.models.dtos.PreTimeDto;
import com.soften.support.gemini_resumo.service.PreControlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PreController.class)
class PreControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PreControlService service;

    @Test
    void createShouldReturnGoneWhenPersistenceIsDisabled() throws Exception {
        PreTimeDto request = new PreTimeDto(null, "John", "Client", LocalDateTime.now(), "10:30", false);

        when(service.create(any(PreTimeDto.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.GONE, "PRE profile persistence is disabled"));

        mockMvc.perform(post("/api/pre-controls")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isGone());
    }

    @Test
    void getAllShouldReturnOk() throws Exception {
        PreTimeDto item = new PreTimeDto(UUID.randomUUID(), "John", "Client", LocalDateTime.now(), "10:30", false);
        Page<PreTimeDto> page = new PageImpl<>(List.of(item));

        when(service.findAll(eq("John"), eq(true), eq(null), eq(null), eq(null), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/pre-controls")
                        .param("name", "John")
                        .param("negociation", "true"))
                .andExpect(status().isOk());

        verify(service).findAll(eq("John"), eq(true), eq(null), eq(null), eq(null), any());
    }
}
