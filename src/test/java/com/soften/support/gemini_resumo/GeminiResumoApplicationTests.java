package com.soften.support.gemini_resumo;

import com.soften.support.gemini_resumo.service.GeminiService;
import com.soften.support.gemini_resumo.service.GeminiIntegrationException;
import com.soften.support.gemini_resumo.service.GoogleFileSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "gemini.api.key=test-key")
@AutoConfigureMockMvc
class GeminiResumoApplicationTests {
    @Autowired MockMvc mockMvc;
    @Autowired ApplicationContext context;
    @MockBean GeminiService geminiService;
    @MockBean GoogleFileSearchService fileSearchService;

    @Test
    void startsWithoutDatabaseAndPingWorks() throws Exception {
        assertTrue(context.getBeansOfType(DataSource.class).isEmpty());
        mockMvc.perform(get("/api/gemini/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void smartReplyEndpointReturnsOneDraftWithoutDatabase() throws Exception {
        when(geminiService.generateInteractive(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("Pode informar a rejeição?");
        assertTrue(context.getBeansOfType(DataSource.class).isEmpty());
        mockMvc.perform(post("/api/gemini/responder").contentType(MediaType.APPLICATION_JSON)
                .content("{\"conversation\":\"Cliente: falha na nota\",\"profile\":\"DIRECT\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reply").value("Pode informar a rejeição?"));
        org.mockito.Mockito.verify(geminiService).generateInteractive(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void summaryEndpointReturnsSummaryWithoutDatabase() throws Exception {
        when(geminiService.generateSummary("chat", null)).thenReturn("resumo");
        mockMvc.perform(post("/api/gemini/resumir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"chat\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("resumo"));
    }

    @Test
    void tipsEndpointUsesCurrentConversationWithoutDatabase() throws Exception {
        String summary = "**PROBLEMA / DÚVIDA:** O cliente não conseguiu emitir NF-e.\n"
                + "**SOLUÇÃO APRESENTADA:** Orientei conferir o certificado digital.\n"
                + "**MÓDULO:** NF-E (NOTA FISCAL ELETRÔNICA)";
        when(geminiService.generateSummary("Conversa atual", null)).thenReturn(summary);
        when(geminiService.ask(contains("Conversa atual")))
                .thenReturn("- Valide se a rejeição persiste após a conferência do certificado.");

        mockMvc.perform(post("/api/chamado/processar-dica")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"Conversa atual\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.fullSummary").value(summary))
                .andExpect(jsonPath("$.SimilarTagsFound").value(0))
                .andExpect(jsonPath("$.solutionsAnalyzed").value(0))
                .andExpect(jsonPath("$.tips[0]").value("Valide se a rejeição persiste após a conferência do certificado."));
    }

    @Test
    void tipsEndpointReturnsGeminiErrorWithoutLeakingDetails() throws Exception {
        when(geminiService.generateSummary("Conversa atual", null)).thenThrow(
                new GeminiIntegrationException("Gemini indisponível", org.springframework.http.HttpStatus.BAD_GATEWAY,
                        "secret-key", new RuntimeException("503")));

        mockMvc.perform(post("/api/chamado/processar-dica")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"Conversa atual\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.erro").value("Gemini indisponível"));
    }

    @Test
    void documentationSearchRouteRemainsAvailable() throws Exception {
        when(geminiService.buscarDocumentacaoOficialSmart("nfe", "manuais"))
                .thenReturn(List.of(new org.springframework.ai.document.Document("doc-1", "Manual NF-e", Map.of("source", "Google File Search"))));

        mockMvc.perform(get("/api/docs/search").param("query", "nfe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("Manual NF-e"));
    }

    @Test
    void legacySaveReturnsGone() throws Exception {
        mockMvc.perform(post("/api/chamado/salvar-resumo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"resumo\"}"))
                .andExpect(status().isGone());
    }
}
