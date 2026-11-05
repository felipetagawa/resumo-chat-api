package com.soften.support.gemini_resumo.docs;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocsIndexTest {

    static DocsIndex sample() throws Exception {
        String json = """
                {"builtAt":"2026-11-05","chunks":[
                 {"t":"Rejeição 539 - Duplicidade de NF-e","b":"siem-nf-e","p":"Soften SIEM","u":"https://d/siem-539","d":"2026-01-02","x":"Consulte a chave de acesso no portal da SEFAZ e reenvie com nova numeração."},
                 {"t":"Rejeição 539 - Duplicidade de NF-e","b":"gerencieaqui-nf-e","p":"GerencieAqui","u":"https://d/ga-539","d":"2025-03-04","x":"No GerencieAqui, acesse Fiscal > NF-e e use Consultar situação."},
                 {"t":"Como cadastrar produto","b":"siem-cadastros","p":"Soften SIEM","u":"https://d/produto","d":"2024-05-06","x":"Acesse Cadastros > Produtos e clique em Novo."},
                 {"t":"Como cadastrar produto","b":"siem-cadastros","p":"Soften SIEM","u":"https://d/produto","d":"2024-05-06","x":"Informe NCM, unidade e preço de venda."},
                 {"t":"Como cadastrar produto","b":"siem-cadastros","p":"Soften SIEM","u":"https://d/produto","d":"2024-05-06","x":"Salve o produto e confira o estoque."}
                ]}""";
        return new DocsIndex(new ObjectMapper().readTree(json));
    }

    @Test void findsRejectionByCodeAndPrefersTheNamedProduct() throws Exception {
        DocsIndex index = sample();
        List<DocsIndex.Hit> hits = index.search("cliente do GerencieAqui com rejeição 539 na nota", null, 3);
        assertEquals("https://d/ga-539", hits.get(0).passage().url());
        assertEquals("https://d/siem-539", index.search("rejeicao 539 no siem", null, 3).get(0).passage().url());
        assertEquals("https://d/siem-539", index.search("rejeição 539", "Soften SIEM", 3).get(0).passage().url());
    }

    @Test void ignoresAccentsPluralsAndStopWords() throws Exception {
        DocsIndex index = sample();
        assertEquals("https://d/produto", index.search("como cadastro os produtos?", null, 1).get(0).passage().url());
        assertTrue(index.search("oi bom dia tudo bem", null, 3).isEmpty());
        assertEquals(DocsIndex.terms("Rejeições"), DocsIndex.terms("rejeicao"));
    }

    @Test void keepsAtMostTwoPassagesPerPage() throws Exception {
        List<DocsIndex.Hit> hits = sample().search("cadastrar produto", null, 10);
        assertEquals(2, hits.stream().filter(h -> h.passage().url().equals("https://d/produto")).count());
    }

    @Test void shippedIndexLoadsAndAnswersACommonQuestion() {
        DocsIndex index = new DocsIndex(new ObjectMapper());
        assertTrue(index.size() > 1000, "index.json should be on the classpath");
        List<DocsIndex.Hit> hits = index.search("como cancelar uma NF-e no Soften SIEM", null, 5);
        assertFalse(hits.isEmpty());
        assertTrue(hits.stream().anyMatch(h -> h.passage().title().toLowerCase().contains("cancel")),
                () -> hits.stream().map(h -> h.passage().title()).toList().toString());
    }
}
