package com.soften.support.gemini_resumo.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ProductCatalog {

    public static final String UNCLEAR_CHOICE = "__UNCLEAR__";

    public record ProductDefinition(String id, String name, String description) {
    }

    private static final List<ProductDefinition> PRODUCTS = List.of(
            new ProductDefinition("44", "NFS-E (NOTA FISCAL ELETRONICA DE SERVIÇO)", "Nota fiscal de serviço municipal: prefeitura, ISS, RPS, código/item de serviço, prestador, tomador e tributação de serviços."),
            new ProductDefinition("2", "COMERCIAL/VENDAS", "Orçamentos, pedidos de venda, vendas, clientes, vendedores e rotinas comerciais do ERP."),
            new ProductDefinition("76", "EDI", "Use quando o atendimento mencionar explicitamente EDI ou troca eletrônica de dados/documentos."),
            new ProductDefinition("74", "REUNIÃO CS - CAPITÃO", "Use somente quando o assunto for explicitamente a reunião interna de CS/Capitão."),
            new ProductDefinition("73", "ARMAZENAMENTO A CADA 100MB", "Contratação, uso ou cobrança de armazenamento adicional por blocos de 100 MB."),
            new ProductDefinition("51", "BOLETOS REGISTRADOS", "Boletos bancários registrados, emissão, registro, remessa, retorno e ocorrências de boleto."),
            new ProductDefinition("16", "ATUALIZACÃO DE VERSÃO", "Atualização de versão do sistema ou necessidade de atualizar o software."),
            new ProductDefinition("78", "BACKUP NUVEM", "Backup em nuvem, cópia, restauração ou serviço de backup remoto."),
            new ProductDefinition("9", "CT-E (CONHECIMENTO ELET. TRANSPORTE)", "CT-e de transporte de cargas: frete, transportadora, tomador, emissão, rejeições e eventos do CT-e."),
            new ProductDefinition("20", "MDF-E (MANIFESTO DO DESTINATARIO)", "MDF-e: manifesto, veículo, motorista, percurso, documentos vinculados, encerramento e rejeições."),
            new ProductDefinition("48", "CTE-OS (CONHECIMENTO ELET. TRANSPORTE PARA OUTROS SERVIÇOS)", "CT-e OS para transporte de pessoas, valores ou outros serviços previstos nesse documento."),
            new ProductDefinition("81", "ENTREGA DE RELATÓRIO FEITO", "Use somente quando o assunto for explicitamente entrega de relatório já realizado."),
            new ProductDefinition("17", "NOVO TERMINAL / ATALHO", "Novo computador/terminal, criação de atalho ou preparação de acesso do sistema em outra estação."),
            new ProductDefinition("39", "CERTIFICADO DIGITAL (A1)", "Certificado digital A1: instalação, vencimento, seleção, erro de certificado ou assinatura com arquivo A1."),
            new ProductDefinition("4", "ESTOQUE", "Saldo, movimentação, entrada/saída, inventário, ajuste, transferência, custo e demais rotinas de estoque."),
            new ProductDefinition("87", "DOCUMENTAÇÃO INTERNA", "Use somente para assunto explicitamente relacionado a documentação interna."),
            new ProductDefinition("12", "FARMACIA/SNGPC/FARMACIA POPULAR", "Rotinas específicas de farmácia, SNGPC ou Farmácia Popular."),
            new ProductDefinition("83", "GERENCIELOG", "Use quando o atendimento mencionar explicitamente o produto/serviço GerencieLog."),
            new ProductDefinition("15", "INSTALACAO SIEM", "Instalação inicial do sistema SIEM."),
            new ProductDefinition("21", "NFC-E (NOTA FISCAL DO CONSUMIDOR ELETRONICA)", "NFC-e/cupom fiscal eletrônico ao consumidor: CSC, QR Code, venda no caixa, consumidor final, emissão e rejeições de NFC-e."),
            new ProductDefinition("79", "CONFIGURAÇÃO DE CONTA - GA", "Configurações da conta/empresa no GerencieAqui, parâmetros gerais e dados da conta."),
            new ProductDefinition("7", "ANDROID", "Aplicativo, dispositivo ou funcionamento do sistema em Android."),
            new ProductDefinition("5", "SPED PIS/COFINS/ICMS/IPI", "SPED fiscal e contribuições: PIS, COFINS, ICMS, IPI, geração, validação e arquivos."),
            new ProductDefinition("82", "COBRANÇA", "Assuntos de cobrança do serviço/empresa quando não forem uma rotina operacional do módulo Financeiro."),
            new ProductDefinition("31", "REINSTALACAO / TRANSFERENCIA SAT", "Reinstalação ou transferência de equipamento/configuração SAT."),
            new ProductDefinition("91", "Pacote Emissor Fiscal 100 (Parceiro)", "Use quando o atendimento mencionar explicitamente o Pacote Emissor Fiscal 100 para parceiro."),
            new ProductDefinition("22", "REPARACAO DO SIEM", "Reparo/correção da instalação ou funcionamento do SIEM."),
            new ProductDefinition("52", "REINSTALAÇÃO / TRANSFERENCIA SIEM", "Reinstalação ou transferência do SIEM para outra máquina/ambiente."),
            new ProductDefinition("19", "POS-VENDA", "Atividades e contatos explicitamente relacionados ao pós-venda."),
            new ProductDefinition("13", "POSTO COMBUSTIVEL/LMC/BOMBAS", "Posto de combustível, LMC, bombas, abastecimento e rotinas específicas de postos."),
            new ProductDefinition("85", "SOFTEN DASH", "Use quando o atendimento mencionar explicitamente o Soften Dash."),
            new ProductDefinition("61", "APLICATIVO EMPRESARIAL", "Use quando o assunto for explicitamente o Aplicativo Empresarial."),
            new ProductDefinition("88", "RENOVAÇÃO DE CONTRATO", "Renovação de contrato, continuidade contratual ou tratativa específica de renovação."),
            new ProductDefinition("56", "RELATÓRIO", "Criação, configuração, emissão, filtro ou dúvida sobre relatórios do sistema."),
            new ProductDefinition("65", "CIOT", "CIOT/ANTT, operação de transporte, pagamento de frete, distância, contratante e dados exigidos no CIOT."),
            new ProductDefinition("32", "CONVERSÃO DE DADOS", "Conversão, importação ou migração de dados entre sistemas/bases."),
            new ProductDefinition("8", "SAT / PAF / FRENTE DE CAIXA", "SAT, PAF, frente de caixa/PDV, equipamento fiscal de varejo e operação de caixa quando não for especificamente NFC-e."),
            new ProductDefinition("64", "IMPRESSORA (HARDWARE)", "Impressora e problemas físicos/configuração de hardware de impressão."),
            new ProductDefinition("18", "INSTALACAO GERENCIADOR A3 - GERENCIE AQUI", "Instalação do gerenciador necessário ao certificado A3 no GerencieAqui."),
            new ProductDefinition("54", "NF-E (IMPORTAÇÃO E EXPORTAÇÃO)", "NF-e especificamente ligada a operações de importação ou exportação."),
            new ProductDefinition("1", "NF-E (NOTA FISCAL ELETRONICA)", "NF-e de mercadorias/produtos: SEFAZ, DANFE, XML, chave de acesso, NCM, CFOP, ICMS, entrada/saída e rejeições de NF-e."),
            new ProductDefinition("3", "FINANCEIRO", "Contas a pagar/receber, baixa, recebimentos, pagamentos, caixa, conciliação e rotinas financeiras do ERP."),
            new ProductDefinition("58", "LOJA VIRTUAL", "Loja virtual/e-commerce próprio, catálogo, pedidos e integração da loja virtual."),
            new ProductDefinition("55", "PROGRAMAÇÃO", "Solicitação, correção ou entrega que depende explicitamente da equipe de programação/desenvolvimento."),
            new ProductDefinition("40", "CERTIFICADO DIGITAL (A3)", "Certificado digital A3: token/cartão, gerenciador, leitura, assinatura, instalação ou erro de certificado A3."),
            new ProductDefinition("75", "CONTATO ATIVO - CS", "Use somente quando o assunto for explicitamente contato ativo realizado por CS."),
            new ProductDefinition("84", "MARKETPLACE", "Integrações com marketplaces como Mercado Livre, Shopee e similares: anúncios, pedidos, estoque, variações e sincronização."),
            new ProductDefinition("47", "RESTAURANTE", "Rotinas específicas de restaurante, mesas, comandas, pedidos e operação de atendimento."),
            new ProductDefinition("68", "AVERBADOR DE SEGURO", "Averbação de seguro relacionada a transporte/carga."),
            new ProductDefinition("6", "SINTEGRA", "Geração, validação e dúvidas sobre SINTEGRA."),
            new ProductDefinition("89", "DC-E (DECLARAÇÃO DE CONTEUDO ELETRONICA)", "DC-e/Declaração de Conteúdo Eletrônica, emissão e validações desse documento."),
            new ProductDefinition("80", "CHAVE DE LIBERAÇÃO", "Chave de liberação/licença do sistema, ativação ou bloqueio relacionado à chave."),
            new ProductDefinition("60", "4LEARN", "Use quando o atendimento mencionar explicitamente o 4Learn."),
            new ProductDefinition("72", "SUPORTE 24 HORAS", "Use quando o assunto for explicitamente o serviço de Suporte 24 Horas."),
            new ProductDefinition("25", "DEMONSTRAÇÃO DE PRODUTO", "Solicitação ou realização de demonstração de produto.")
    );

    private ProductCatalog() {
    }

    public static List<ProductDefinition> all() {
        return PRODUCTS;
    }

    public static Optional<ProductDefinition> findById(String id) {
        return PRODUCTS.stream().filter(product -> product.id().equals(id)).findFirst();
    }

    public static Map<String, Object> asJevCriteria() {
        Map<String, Object> criteria = new LinkedHashMap<>();
        for (ProductDefinition product : PRODUCTS) {
            criteria.put(product.id(), product.name() + " — " + product.description());
        }
        criteria.put(
                UNCLEAR_CHOICE,
                "A conversa não contém informação suficiente para distinguir o produto com segurança. " +
                        "Use para termos genéricos ou ambíguos, como apenas 'nota', 'erro', 'sistema' ou 'não funciona', " +
                        "sem evidências que diferenciem os produtos."
        );
        return criteria;
    }
}
