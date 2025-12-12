package com.soften.support.gemini_resumo.utils;

import com.soften.support.gemini_resumo.models.enums.ModulesCalled;

public class ModuleMapper {

    public static ModulesCalled map(String module) {
        if (module == null) return ModulesCalled.GENERIC;

        String h = module.toUpperCase();

        if (h.contains("NF-E (NOTA FISCAL ELETRÔNICA)")) return ModulesCalled.NFE;
        if (h.contains("NFC-E (NOTA FISCAL DO CONSUMIDOR ELETRÔNICA)")) return ModulesCalled.NFCE;
        if (h.contains("MDF-E")) return ModulesCalled.MDFE;
        if (h.contains("CT-E")) return ModulesCalled.CTE;
        if (h.contains("FRENTE DE CAIXA")) return ModulesCalled.FRENTE_DE_CAIXA;
        if (h.contains("CERTIFICADO")) return ModulesCalled.CERTIFICADO;
        if (h.contains("CONFIGURAÇÃO DE CONTA")) return ModulesCalled.CONFIGURACAO_DE_CONTA;
        if (h.contains("COMERCIAL/VENDAS")) return ModulesCalled.COMERCIAL_VENDAS;
        if (h.contains("ESTOQUE")) return ModulesCalled.ESTOQUE;
        if (h.contains("FINANCEIRO")) return ModulesCalled.FINANCEIRO;
        if (h.contains("BOLETOS")) return ModulesCalled.BOLETOS;
        if (h.contains("MARKETPLACE / LOJA VIRTUAL")) return ModulesCalled.MARKETPLACE_LOJA_VIRTUAL;
        if (h.contains("RESTAURANTE")) return ModulesCalled.RESTAURANTE;
        if (h.contains("RELATÓRIO")) return ModulesCalled.RELATORIO;

        return ModulesCalled.GENERIC;
    }
}
