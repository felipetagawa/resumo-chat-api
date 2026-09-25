package com.soften.support.gemini_resumo.service;

import com.soften.support.gemini_resumo.models.dtos.SummaryDto;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SuggestionServiceTest {
    private final GeminiService gemini = mock(GeminiService.class);
    private final SuggestionService service = new SuggestionService(gemini);
    private final SummaryDto summary = new SummaryDto(
            "Resumo do atendimento: falha na emissão de NF-e; certificado conferido.",
            null,
            "Falha na emissão de NF-e.",
            "Conferi o certificado digital.",
            null);

    @Test
    void acceptsDifferentListMarkersAndLimitsTheResult() {
        when(gemini.ask(anyString())).thenReturn("• Verifique o retorno da SEFAZ.\n"
                + "- Confira o ambiente de emissão.\n"
                + "* Valide a série da nota.\n"
                + "1. Teste com uma nota de homologação.\n"
                + "2) Confirme a versão do leiaute.\n"
                + "3. Analise o código da rejeição.\n"
                + "4. Dica excedente.");

        assertEquals(List.of("Verifique o retorno da SEFAZ.", "Confira o ambiente de emissão.",
                "Valide a série da nota.", "Teste com uma nota de homologação.",
                "Confirme a versão do leiaute.", "Analise o código da rejeição."),
                service.generateTips("Atendimento atual", summary));
    }

    @Test
    void acceptsUsefulProseWithoutListMarkers() {
        when(gemini.ask(anyString())).thenReturn("Verifique se a rejeição ainda ocorre em uma nova tentativa de emissão.\n\n"
                + "Se persistir, compare o código retornado pela SEFAZ com os dados da nota antes de alterar a configuração.");

        assertEquals(List.of("Verifique se a rejeição ainda ocorre em uma nova tentativa de emissão.",
                "Se persistir, compare o código retornado pela SEFAZ com os dados da nota antes de alterar a configuração."),
                service.generateTips("Atendimento atual", summary));
    }

    @Test
    void limitsLengthOfUnstructuredProse() {
        when(gemini.ask(anyString())).thenReturn("Verifique " + "a".repeat(800));

        List<String> tips = service.generateTips("Atendimento atual", summary);

        assertEquals(1, tips.size());
        assertTrue(tips.get(0).length() <= 401);
    }

    @Test
    void treatsEmptyGeminiResponseAsIntegrationFailure() {
        when(gemini.ask(anyString())).thenReturn("   ");

        assertThrows(GeminiIntegrationException.class,
                () -> service.generateTips("Atendimento atual", summary));
    }

    @Test
    void separatesCompletedActionsFromFutureSuggestions() {
        when(gemini.ask(anyString())).thenReturn("- Conferi o certificado digital.\n"
                + "- Valide se a rejeição persiste após a conferência do certificado.");

        assertEquals(List.of("Valide se a rejeição persiste após a conferência do certificado."),
                service.generateTips("Atendimento atual", summary));

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(gemini).ask(prompt.capture());
        assertTrue(prompt.getValue().contains("PROBLEMA ATUAL:\nFalha na emissão de NF-e."));
        assertTrue(prompt.getValue().contains("AÇÕES JÁ REALIZADAS NO ATENDIMENTO:\nConferi o certificado digital."));
        assertTrue(prompt.getValue().contains("CONTEXTO DO RESUMO ATUAL:"));
    }

    @Test
    void excludesAnActionAlreadyRecordedAmongSeveralCompletedActions() {
        SummaryDto severalActions = new SummaryDto(summary.fullSummary(), null, summary.problem(),
                "Conferi o certificado digital. Reiniciei o emissor.", null);
        when(gemini.ask(anyString())).thenReturn("- Reiniciei o emissor.\n"
                + "- Confirme o código da rejeição numa nova tentativa.");

        assertEquals(List.of("Confirme o código da rejeição numa nova tentativa."),
                service.generateTips("Atendimento atual", severalActions));
    }

    @Test
    void propagatesRealGeminiIntegrationFailures() {
        GeminiIntegrationException failure = new GeminiIntegrationException("Falha Gemini",
                org.springframework.http.HttpStatus.BAD_GATEWAY, new IllegalStateException("Falha de rede"));
        when(gemini.ask(anyString())).thenThrow(failure);

        assertSame(failure, assertThrows(GeminiIntegrationException.class,
                () -> service.generateTips("Atendimento atual", summary)));
    }
}
