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
    @Test void closerWithSubstanceKeepsItsCallToAction() {
        assertEquals("Vou precisar acessar sua máquina. Fico à disposição para acessar via AnyDesk assim que me passar o código.",
                ReplyPolish.apply("Vou precisar acessar sua máquina. Fico à disposição para acessar via AnyDesk assim que me passar o código."));
        assertEquals("Já reenviei a nota e ela foi autorizada.",
                ReplyPolish.apply("Peço desculpas pelo transtorno, já reenviei a nota e ela foi autorizada."));
    }
    @Test void removesLongerOpeningCliches() {
        assertEquals("Pode me mandar o XML?", ReplyPolish.apply("Entendo sua frustração com a demora. Pode me mandar o XML?"));
        assertEquals("Vou olhar.", ReplyPolish.apply("Compreendo a sua preocupação. Vou olhar."));
        assertEquals("Pode mandar o XML?", ReplyPolish.apply("Agradeço o contato! Pode mandar o XML?"));
        assertEquals("Pode mandar o XML?", ReplyPolish.apply("Agradeço a paciência. Pode mandar o XML?"));
    }
    @Test void removesFillerAfterClosingPhrases() {
        assertEquals("Pode mandar o XML.", ReplyPolish.apply("Pode mandar o XML. Qualquer dúvida, é só chamar!"));
        assertEquals("Pode mandar o XML.", ReplyPolish.apply("Pode mandar o XML. Estou à disposição para o que precisar."));
    }
    @Test void removesOrdinaryPodeMeChamarCloser() {
        assertEquals("Pode mandar o XML.", ReplyPolish.apply("Pode mandar o XML. Qualquer dúvida, pode me chamar!"));
        assertEquals("Pode mandar o XML.", ReplyPolish.apply("Pode mandar o XML. Qualquer dúvida, pode me avisar."));
        assertEquals("Pode mandar o XML.", ReplyPolish.apply("Pode mandar o XML. Qualquer outra dúvida, pode falar comigo!"));
    }
    @Test void keepsRealInstructionsAfterQualquerDuvida() {
        for (String s : new String[]{"Qualquer dúvida, pode mandar o XML.", "Qualquer dúvida no passo 2, pode reiniciar o serviço."})
            assertEquals(s, ReplyPolish.apply(s));
    }
    @Test void removesMoreThanThreeConsecutiveOpenersAndClosers() {
        assertEquals("Pode mandar o XML.", ReplyPolish.apply("Certo! Perfeito! Claro! Compreendo. Pode mandar o XML."));
        assertEquals("Pode mandar o XML.", ReplyPolish.apply(
                "Pode mandar o XML. Espero ter ajudado. Qualquer dúvida, pode me chamar! Agradeço o contato. Fico à disposição!"));
        assertEquals("", ReplyPolish.apply("Certo! Perfeito! Claro! Compreendo. Fico à disposição! Espero ter ajudado. Agradeço o contato. Qualquer dúvida, pode me chamar!"));
    }
}
