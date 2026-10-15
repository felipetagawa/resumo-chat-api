package com.soften.support.gemini_resumo.controller;
import com.soften.support.gemini_resumo.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class ProblemLineControllerTest {
    GeminiService gemini;
    MockMvc mvc;
    ObjectMapper json = new ObjectMapper();
    @BeforeEach void setup() {
        gemini = mock(GeminiService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ProblemLineController(gemini, new SmartReplyRateLimiter(30))).build();
    }
    ResultActions send(Object body) throws Exception {
        return mvc.perform(post("/api/gemini/linha-problema").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }
    @Test void returnsNormalizedLine() throws Exception {
        when(gemini.generateProblemLine("erro no xml")).thenReturn("  \"Certificado novo não emite DANFE.\" \n");
        send(Map.of("mensagens", "erro no xml")).andExpect(status().isOk())
                .andExpect(jsonPath("$.linha").value("Certificado novo não emite DANFE"));
    }
    @Test void noProblemMarkerBecomesEmptyLine() throws Exception {
        when(gemini.generateProblemLine(anyString())).thenReturn("SEM_PROBLEMA");
        send(Map.of("mensagens", "bom dia")).andExpect(status().isOk()).andExpect(jsonPath("$.linha").value(""));
    }
    @Test void rejectsInvalidInputBeforeGeneration() throws Exception {
        for (Object body : new Object[]{Map.of(), Map.of("mensagens", " "), Map.of("mensagens", "x".repeat(1501))})
            send(body).andExpect(status().isBadRequest());
        verifyNoInteractions(gemini);
    }
    @Test void geminiFailureIsGeneric() throws Exception {
        when(gemini.generateProblemLine(anyString())).thenThrow(new GeminiIntegrationException("segredo técnico", HttpStatus.BAD_GATEWAY, (Throwable) null));
        send(Map.of("mensagens", "erro")).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.erro").value("Não foi possível resumir o problema."));
    }
    @Test void sharedLimiter() throws Exception {
        mvc = MockMvcBuilders.standaloneSetup(new ProblemLineController(gemini, new SmartReplyRateLimiter(1))).build();
        when(gemini.generateProblemLine(anyString())).thenReturn("Erro no XML");
        send(Map.of("mensagens", "erro")).andExpect(status().isOk());
        send(Map.of("mensagens", "erro")).andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "60"));
    }
    @Test void normalizeTruncatesAt70() {
        String line = ProblemLineController.normalize("a".repeat(100));
        assertEquals(70, line.length());
        assertEquals('…', line.charAt(69));
    }
}
