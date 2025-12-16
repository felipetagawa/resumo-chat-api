package com.soften.support.gemini_resumo.controller;

import org.springframework.ai.document.Document;
// Removed unused import: org.springframework.ai.vectorstore.VectorStore
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/docs")
public class DocumentationController {

    private final com.soften.support.gemini_resumo.service.GeminiService geminiService;
    private final com.soften.support.gemini_resumo.service.GoogleFileSearchService googleFileSearchService;

    public DocumentationController(com.soften.support.gemini_resumo.service.GeminiService geminiService,
            com.soften.support.gemini_resumo.service.GoogleFileSearchService googleFileSearchService) {
        this.geminiService = geminiService;
        this.googleFileSearchService = googleFileSearchService;
    }

    @PostMapping(consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> addDocumentation(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "categoria", defaultValue = "GERAL") String categoria) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is required");
        }

        try {
            // Upload to Google File Search
            String originalFilename = file.getOriginalFilename();
            String contentType = file.getContentType();

            // Fallback for nulls
            if (originalFilename == null)
                originalFilename = "document";
            if (contentType == null)
                contentType = "application/octet-stream";

            String resultName = googleFileSearchService.uploadFile(originalFilename, file.getBytes(), contentType);

            return ResponseEntity.ok(Map.of(
                    "message", "File uploaded to Google File Search",
                    "id", resultName,
                    "categoria", categoria));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error uploading to Google: " + e.getMessage());
        }
    }

    // Deletion is temporarily disabled as we moved to Google File Search
    // @DeleteMapping("/{id}")
    // public ResponseEntity<?> deleteDocumentation(@PathVariable String id) {
    // vectorStore.delete(List.of(id));
    // return ResponseEntity.ok(Map.of("message", "Documentation deleted (if it
    // existed)"));
    // }

    @GetMapping("/search")
    public ResponseEntity<?> searchDocumentation(
            @RequestParam String query,
            @RequestParam(required = false, defaultValue = "manuais") String categoria) {
        // Use Smart RAG instead of direct vector search, with category filter
        List<Document> docs = geminiService.buscarDocumentacaoOficialSmart(query, categoria);

        // Map to simpler response including ID
        var response = docs.stream().map(d -> Map.of(
                "id", d.getId(),
                "content", d.getText(),
                "metadata", d.getMetadata())).toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/list")
    public ResponseEntity<?> listAllFiles() {
        try {
            var files = googleFileSearchService.listAllFiles();
            return ResponseEntity.ok(Map.of(
                    "totalFiles", files.size(),
                    "files", files));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Erro ao listar arquivos: " + e.getMessage()));
        }
    }
}
