package com.soften.support.gemini_resumo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soften.support.gemini_resumo.config.TypeSafeApiProperties;
import com.soften.support.gemini_resumo.models.dtos.DocumentationCandidateDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class JevDocumentationClassificationServiceTest {

    private TypeSafeApiProperties properties;
    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private ObjectMapper objectMapper;
    private JevDocumentationClassificationService service;

    @BeforeEach
    void setUp() {
        properties = new TypeSafeApiProperties();
        properties.setKey("test-key");
        properties.setSystemOneUrl("https://api.typesafe.ai/v1/systemone");
        objectMapper = new ObjectMapper();
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        service = new JevDocumentationClassificationService(properties, restTemplate, objectMapper);
    }

    @Test
    void clearDocumentationReturnsSingleSuggestion() throws Exception {
        var candidates = List.of(
                new DocumentationCandidateDto("1339", "Rejeição 610: Total da NF-e difere do Somatório dos itens"),
                new DocumentationCandidateDto("1702", "Rejeição 533: Total da BC ICMS-ST difere do somatório dos itens")
        );

        server.expect(once(), requestTo(properties.getSystemOneUrl()))
                .andRespond(withSuccess(
                        response(candidates, "1339", 0.98d, Map.of("1339", 0.96d, "1702", 0.02d,
                                JevDocumentationClassificationService.UNCLEAR_CHOICE, 0.02d)),
                        MediaType.APPLICATION_JSON
                ));

        var result = service.classify("Cliente recebeu rejeição 610 ao emitir NF-e.", candidates);

        assertEquals("single", result.mode());
        assertEquals(1, result.suggestions().size());
        assertEquals("1339", result.suggestions().get(0).id());
    }

    @Test
    void unclearDocumentationCanAbstain() throws Exception {
        var candidates = List.of(
                new DocumentationCandidateDto("1", "Rejeição 610"),
                new DocumentationCandidateDto("2", "Rejeição 533")
        );

        server.expect(once(), requestTo(properties.getSystemOneUrl()))
                .andRespond(withSuccess(
                        response(candidates, JevDocumentationClassificationService.UNCLEAR_CHOICE, 0.90d,
                                Map.of("1", 0d, "2", 0d,
                                        JevDocumentationClassificationService.UNCLEAR_CHOICE, 1d)),
                        MediaType.APPLICATION_JSON
                ));

        var result = service.classify("Cliente apenas disse que deu erro.", candidates);

        assertEquals("uncertain", result.mode());
        assertTrue(result.suggestions().isEmpty());
    }

    @Test
    void rejectsMoreThan240Candidates() {
        var candidates = java.util.stream.IntStream.range(0, 241)
                .mapToObj(i -> new DocumentationCandidateDto(String.valueOf(i), "Doc " + i))
                .toList();

        assertThrows(
                IllegalArgumentException.class,
                () -> service.classify("contexto", candidates)
        );
    }

    private String response(
            List<DocumentationCandidateDto> candidates,
            String choice,
            double confidence,
            Map<String, Double> overrides
    ) throws Exception {
        Map<String, Double> probabilities = new LinkedHashMap<>();
        for (DocumentationCandidateDto candidate : candidates) {
            probabilities.put(candidate.id(), 0d);
        }
        probabilities.put(JevDocumentationClassificationService.UNCLEAR_CHOICE, 0d);
        probabilities.putAll(overrides);

        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("type", "choice");
        answer.put("choice", choice);
        answer.put("confidence", confidence);
        answer.put("probabilities", probabilities);

        return objectMapper.writeValueAsString(Map.of(
                "model", "jev-latest",
                "answers", Map.of("documentation", answer)
        ));
    }
}
