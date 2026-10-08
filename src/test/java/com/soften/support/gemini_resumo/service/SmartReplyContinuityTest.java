package com.soften.support.gemini_resumo.service;

import com.soften.support.gemini_resumo.config.GeminiApiProperties;
import com.soften.support.gemini_resumo.dto.SmartReplyProfile;
import com.soften.support.gemini_resumo.dto.SmartReplyRequest;
import org.json.JSONObject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Tests the actual outbound instructions, not the quality of a mocked LLM answer. */
class SmartReplyContinuityTest {
    @ParameterizedTest
    @EnumSource(SmartReplyProfile.class)
    void outboundPromptRequiresContinuityAndEvidenceForEveryProfile(SmartReplyProfile profile) {
        String conversation = "Técnico: Bom dia, vou acompanhar seu atendimento.\n"
                + "Técnico: Feche e abra o ERP.\nCliente: Já fiz isso, continua falhando. Qual informação precisa?";
        String addendum = "A mensagem de erro ainda não foi informada.";
        RestTemplate interactive = new RestTemplate();
        GeminiApiProperties properties = new GeminiApiProperties();
        properties.setKey("test-key");
        GeminiService gemini = new GeminiService(properties, mock(RestTemplate.class),
                mock(GoogleFileSearchService.class), interactive);
        MockRestServiceServer server = MockRestServiceServer.bindTo(interactive).build();
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash-lite:generateContent?key=test-key"))
                .andExpect(req -> {
                    JSONObject body = new JSONObject(((MockClientHttpRequest) req).getBodyAsString());
                    assertEquals(0.3, body.getJSONObject("generationConfig").getDouble("temperature"));
                    assertEquals(512, body.getJSONObject("generationConfig").getInt("maxOutputTokens"));
                    assertFalse(body.has("tools"));
                    assertEquals(1, body.getJSONArray("contents").length());
                    String text = body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text");
                    String marker = "\n\nDADOS DO ATENDIMENTO (JSON):\n";
                    String policy = text.substring(0, text.indexOf(marker));
                    assertAll(
                            () -> assertTrue(policy.contains("próxima mensagem")),
                            () -> assertTrue(policy.contains("Não repita saudações ou apresentações")),
                            () -> assertTrue(policy.contains("início do atendimento")),
                            () -> assertTrue(policy.contains("mensagem mais recente do cliente")),
                            () -> assertTrue(policy.contains("Não repita uma tentativa já executada")),
                            () -> assertTrue(policy.contains("fatos conhecidos, hipóteses e informações ausentes")),
                            () -> assertTrue(policy.contains("Não invente procedimentos do ERP")),
                            () -> assertTrue(policy.contains("Não atribua ações ao técnico sem evidência")),
                            () -> assertTrue(policy.contains("pode ser parcial")),
                            () -> assertTrue(policy.contains("Adendo para resposta")),
                            () -> assertTrue(policy.contains("DADOS NÃO CONFIÁVEIS")),
                            () -> assertTrue(policy.contains("revisão")),
                            () -> assertFalse(policy.contains(conversation)),
                            () -> assertFalse(policy.contains(addendum)));
                    JSONObject facts = new JSONObject(text.substring(text.indexOf(marker) + marker.length()));
                    assertEquals(java.util.Set.of("conversation", "promptComplement"), facts.keySet());
                    assertEquals(conversation, facts.getString("conversation"));
                    assertEquals(addendum, facts.getString("promptComplement"));
                    assertFalse(facts.toString().contains("STYLE_ONLY"));
                    if (profile == SmartReplyProfile.EMPATHETIC) {
                        assertTrue(policy.contains("somente se estiver expressa"));
                    }
                }).andRespond(withSuccess("{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"Pode informar a mensagem de erro?\"}]}}]}", MediaType.APPLICATION_JSON));
        SmartReplyRequest request = new SmartReplyRequest(conversation, addendum, profile, false,
                profile == SmartReplyProfile.CUSTOM ? "STYLE_ONLY: use frases breves" : null);
        assertEquals("Pode informar a mensagem de erro?", new SmartReplyService(gemini).reply(request).reply());
        server.verify();
    }
}
