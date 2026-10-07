package com.soften.support.gemini_resumo.controller;
import com.soften.support.gemini_resumo.dto.*;
import com.soften.support.gemini_resumo.service.*;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class SmartReplyControllerTest {
    SmartReplyService service;
    MockMvc mvc;
    ObjectMapper json=new ObjectMapper();
    @BeforeEach void setup() {
        service=mock(SmartReplyService.class);
        mvc=MockMvcBuilders.standaloneSetup(new SmartReplyController(service,new SmartReplyRateLimiter(30))).build();
        when(service.reply(any())).thenReturn(new SmartReplyResponse("Pode informar o erro?"));
    }
    ResultActions send(Object body) throws Exception {
        return mvc.perform(post("/api/gemini/responder").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }
    @Test void validAndOptionalFields() throws Exception {
        for (String p:new String[]{"DIRECT","EMPATHETIC","DIDACTIC"})
            send(Map.of("conversation","Cliente: oi","profile",p)).andExpect(status().isOk()).andExpect(jsonPath("$.reply").value("Pode informar o erro?"));
        verify(service,times(3)).reply(any());
    }
    @Test void rejectsInvalidInputBeforeGeneration() throws Exception {
        Object[] bodies={Map.of("profile","DIRECT"),Map.of("conversation"," ","profile","DIRECT"),
            Map.of("conversation","oi"),Map.of("conversation","oi","profile","JEV"),
            Map.of("conversation","x".repeat(20001),"profile","DIRECT"),
            Map.of("conversation","oi","profile","DIRECT","promptComplement","x".repeat(2001)),
            Map.of("conversation",12,"profile","DIRECT"),Map.of("conversation","oi","profile",0),
            Map.of("conversation","oi","profile","DIRECT","regenerate","true"),
            Map.of("conversation","oi","profile","DIRECT","prompt","override"),
            Map.of("conversation","oi","profile","DIRECT","privateNote","secret")};
        for(Object b:bodies) send(b).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void boundaryLimitsAndSafeFailure() throws Exception {
        send(Map.of("conversation","x".repeat(20000),"profile","DIRECT","promptComplement","x".repeat(2000),"regenerate",true)).andExpect(status().isOk());
        when(service.reply(any())).thenThrow(new GeminiIntegrationException("technical secret",HttpStatus.BAD_GATEWAY,(Throwable)null));
        send(Map.of("conversation","oi","profile","DIRECT")).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.erro").value("Não foi possível sugerir uma resposta. Tente novamente."));
    }
    @Test void endpointSpecificLimiter() throws Exception {
        mvc=MockMvcBuilders.standaloneSetup(new SmartReplyController(service,new SmartReplyRateLimiter(1))).build();
        send(Map.of("conversation","oi","profile","DIRECT")).andExpect(status().isOk());
        send(Map.of("conversation","oi","profile","DIRECT")).andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","60"));
        verify(service, times(1)).reply(any());
    }
}
