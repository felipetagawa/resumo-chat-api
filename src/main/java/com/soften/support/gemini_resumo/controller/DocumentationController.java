package com.soften.support.gemini_resumo.controller;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/docs")
public class DocumentationController {

    private final VectorStore vectorStore;

    public DocumentationController(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @PostMapping
    public ResponseEntity<?> addDocumentation(@RequestBody Map<String, String> body) {
        String content = body.get("content");
        String categoria = body.getOrDefault("categoria", "GERAL");

        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest().body("Content is required");
        }

        Document doc = new Document(content, Map.of(
                "tipo", "documentacao_oficial",
                "categoria", categoria,
                "titulo", content.length() > 50 ? content.substring(0, 47) + "..." : content));

        vectorStore.add(List.of(doc));
        return ResponseEntity.ok(Map.of("message", "Documentation added", "id", doc.getId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteDocumentation(@PathVariable String id) {
        vectorStore.delete(List.of(id));
        return ResponseEntity.ok(Map.of("message", "Documentation deleted (if it existed)"));
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchDocumentation(@RequestParam String query) {
        List<Document> docs = vectorStore.similaritySearch(
                org.springframework.ai.vectorstore.SearchRequest.builder()
                        .query(query)
                        .filterExpression("tipo == 'documentacao_oficial'")
                        .topK(10)
                        .build());

        // Map to simpler response including ID
        var response = docs.stream().map(d -> Map.of(
                "id", d.getId(),
                "content", d.getText(),
                "metadata", d.getMetadata())).toList();

        return ResponseEntity.ok(response);
    }
}
