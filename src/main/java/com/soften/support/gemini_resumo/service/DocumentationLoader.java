package com.soften.support.gemini_resumo.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class DocumentationLoader implements CommandLineRunner {

    private final VectorStore vectorStore;

    public DocumentationLoader(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void run(String... args) throws Exception {

        // TODO: Em produção, verificar se já existem dados antes de inserir
        // Para demo/teste, vamos inserir alguns dados mockados se o banco estiver
        // vazio?
        // A API do VectorStore não tem "count" fácil, mas podemos fazer uma busca
        // dummy.

        // Simulação de documentações
        List<String> docs = List.of(
                "Erro: IE do destinatario nao informada. Solução: Verificar cadastro do cliente e preencher Inscrição Estadual. Se isento, usar ISENTO.",
                "Rejeição: Falha no Schema XML do lote de NFe. Solução: Verificar caracteres especiais no cadastro de produto ou cliente.",
                "Erro: Certificado Digital vencido. Solução: Solicitar ao cliente a renovação do certificado e instalar novamente no sistema.",
                "Lentidão no sistema ao abrir vendas. Solução: Verificar conexão com internet e limpar cache do navegador/sistema.",
                "Impressora fiscal não responde. Solução: Verificar cabos, drivers e se a porta COM está correta nas configurações.");

        System.out.println("Verificando VectorStore...");
        // Ingestão simples
        // No RAG real, isso viria de um CSV ou Banco de Dados na startup ou via
        // endpoint
        for (String content : docs) {
            Document doc = new Document(content, Map.of("tipo", "suporte_tecnico"));
            vectorStore.add(List.of(doc));
        }
        System.out.println("Documentações de teste carregadas no VectorStore.");
    }
}
