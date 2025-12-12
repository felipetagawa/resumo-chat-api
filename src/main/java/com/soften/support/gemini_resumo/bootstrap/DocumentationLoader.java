package com.soften.support.gemini_resumo.bootstrap;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class DocumentationLoader implements CommandLineRunner {

    private final VectorStore vectorStore;
    private final ResourcePatternResolver resourcePatternResolver;

    public DocumentationLoader(VectorStore vectorStore, ResourcePatternResolver resourcePatternResolver) {
        this.vectorStore = vectorStore;
        this.resourcePatternResolver = resourcePatternResolver;
    }

    @Override
    public void run(String... args) throws Exception {
        List<Document> documents = new ArrayList<>();
        String currentSection = "GERAL";

        Resource[] resources = resourcePatternResolver.getResources("classpath:documentation_data_part*.txt");

        if (resources.length == 0) {
            System.out.println("No documentation data files found.");
            return;
        }

        // Check if data is already loaded to avoid duplicates
        try {
            List<Document> existing = vectorStore.similaritySearch(
                    org.springframework.ai.vectorstore.SearchRequest.builder()
                            .query("nota") // Generic query to check existence
                            .filterExpression("tipo == 'documentacao_oficial'")
                            .topK(1)
                            .build());
            if (!existing.isEmpty()) {
                System.out.println("Documentation data already exists in VectorStore. Skipping initialization.");
                return;
            }
        } catch (Exception e) {
            System.out.println("Could not check for existing documents: " + e.getMessage() + ". Proceeding with load.");
        }

        for (Resource resource : resources) {
            System.out.println("Processing file: " + resource.getFilename());
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty())
                        continue;

                    // Section Header
                    if (line.startsWith("===") && line.endsWith("===")) {
                        currentSection = line.replace("=", "").trim();
                        System.out.println("Found Section: " + currentSection);
                        continue;
                    }

                    // Skip instructional text
                    if (line.toLowerCase().startsWith("aqui est")) {
                        continue;
                    }

                    // Handle numbered lists (e.g. "1. Something")
                    // But prevent stripping valid error codes that might look like lists if
                    // formatted weirdly?
                    // Error codes usually "375, " or "400 -".
                    // List indices usually "1. ".
                    if (line.matches("^\\d+\\.\\s.*")) {
                        line = line.replaceFirst("^\\d+\\.\\s", "").trim();
                    }

                    // Metadata: tipo=documentacao_oficial, categoria=currentSection
                    Document doc = new Document(line, Map.of(
                            "tipo", "documentacao_oficial",
                            "categoria", currentSection,
                            "titulo", line));
                    documents.add(doc);
                }
            }
        }

        if (!documents.isEmpty()) {
            System.out.println("Loading " + documents.size() + " documents into VectorStore...");
            try {
                // Batch add
                vectorStore.add(documents);
                System.out.println("Documents loaded successfully.");
            } catch (Exception e) {
                System.err.println("Error loading documents: " + e.getMessage());
                // Don't fail the startup, just log error
            }
        } else {
            System.out.println("No documents found to load.");
        }
    }
}
