package com.soften.support.gemini_resumo.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProductCatalogTest {

    @Test
    void catalogContainsAll55UniqueCrmProducts() {
        assertEquals(55, ProductCatalog.all().size());
        assertEquals(
                55,
                ProductCatalog.all().stream().map(ProductCatalog.ProductDefinition::id).distinct().count()
        );
        assertEquals(
                55,
                ProductCatalog.all().stream().map(ProductCatalog.ProductDefinition::name).distinct().count()
        );
    }

    @Test
    void catalogKeepsKnownCrmIds() {
        assertEquals("NF-E (NOTA FISCAL ELETRONICA)", ProductCatalog.findById("1").orElseThrow().name());
        assertEquals("NFC-E (NOTA FISCAL DO CONSUMIDOR ELETRONICA)", ProductCatalog.findById("21").orElseThrow().name());
        assertEquals("NFS-E (NOTA FISCAL ELETRONICA DE SERVIÇO)", ProductCatalog.findById("44").orElseThrow().name());
        assertEquals("ESTOQUE", ProductCatalog.findById("4").orElseThrow().name());
    }
}
