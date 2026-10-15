package com.soften.support.gemini_resumo.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReplyPolishTest {
    @Test void removesClosingClichesAtTheEnd() {
        assertEquals("Consegue me mandar o XML?", ReplyPolish.apply("Consegue me mandar o XML? Fico à disposição!"));
        assertEquals("Consegue me mandar o XML?", ReplyPolish.apply("Consegue me mandar o XML?\n\nQualquer dúvida, estou à disposição. 😊"));
        assertEquals("Vou verificar o cadastro.", ReplyPolish.apply("Vou verificar o cadastro. Espero ter ajudado! Agradeço a sua paciência."));
        assertEquals("Pode reenviar a nota.", ReplyPolish.apply("Pode reenviar a nota. Peço desculpas pelo transtorno."));
    }
    @Test void removesOpeningCliches() {
        assertEquals("Pode me mandar o print?", ReplyPolish.apply("Entendo sua situação. Pode me mandar o print?"));
        assertEquals("Pode mandar o XML.", ReplyPolish.apply("Claro! Pode mandar o XML."));
        assertEquals("Vou verificar.", ReplyPolish.apply("Entendo, vou verificar."));
        assertEquals("Segue o passo.", ReplyPolish.apply("Prezado cliente, segue o passo."));
    }
    @Test void keepsContentThatOnlyLooksSimilar() {
        for (String s : new String[]{"Entendo que o sistema travou ao emitir, certo?", "Certo que é a nota 123? Me confirma.",
                "Se ficar alguma dúvida no passo 2 me avisa que eu te ligo.", "Fica à vontade pra mandar o XML."})
            assertEquals(s, ReplyPolish.apply(s));
    }
    @Test void returnsEmptyWhenOnlyClichesRemain() {
        assertEquals("", ReplyPolish.apply("Fico à disposição para qualquer dúvida!"));
        assertEquals("", ReplyPolish.apply("Entendo sua frustração. Qualquer dúvida estou à disposição."));
        assertEquals("", ReplyPolish.apply("Certo!"));
    }
}
