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
            @RequestParam(value = "categoria", required = false) String categoria,
            @RequestParam(value = "modulo", required = false) String modulo,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "descricao", required = false) String descricao) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is required");
        }

        try {
            String originalFilename = file.getOriginalFilename();
            String contentType = file.getContentType();

            if (originalFilename == null)
                originalFilename = "document";
            if (contentType == null)
                contentType = "application/octet-stream";

            // Prepara metadados customizados
            java.util.Map<String, String> customMetadata = new java.util.HashMap<>();

            if (categoria != null && !categoria.isBlank()) {
                customMetadata.put("categoria", categoria);
            }
            if (modulo != null && !modulo.isBlank()) {
                customMetadata.put("modulo", modulo);
            }
            if (tags != null && !tags.isBlank()) {
                customMetadata.put("tags", tags);
            }
            if (descricao != null && !descricao.isBlank()) {
                customMetadata.put("descricao", descricao);
            }

            // Padrão: Upload para o store de MANUAIS
            String resultName;
            if (!customMetadata.isEmpty()) {
                resultName = googleFileSearchService.uploadFileWithMetadataToManuals(
                        originalFilename,
                        file.getBytes(),
                        contentType,
                        customMetadata);
            } else {
                resultName = googleFileSearchService.uploadFileToManuals(
                        originalFilename,
                        file.getBytes(),
                        contentType);
            }

            return ResponseEntity.ok(Map.of(
                    "message", "File uploaded to Google File Search",
                    "id", resultName,
                    "metadata", customMetadata.isEmpty() ? "Nenhum metadata fornecido" : customMetadata));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error uploading to Google: " + e.getMessage());
        }
    }

    /**
     * Upload de arquivos para o Classification Store (frases de classificação).
     * Endpoint dedicado para separar uploads de classificação dos manuais.
     */
    @PostMapping(value = "/classification", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> addClassification(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "categoria", required = false) String categoria,
            @RequestParam(value = "modulo", required = false) String modulo,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "descricao", required = false) String descricao) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is required");
        }

        try {
            String originalFilename = file.getOriginalFilename();
            String contentType = file.getContentType();

            if (originalFilename == null)
                originalFilename = "classification_document";
            if (contentType == null)
                contentType = "application/octet-stream";

            // Adiciona prefixo CLASS_ para identificar arquivos de classificação
            String displayName = originalFilename.startsWith("CLASS_")
                    ? originalFilename
                    : "CLASS_" + originalFilename;

            // Prepara metadados customizados
            java.util.Map<String, String> customMetadata = new java.util.HashMap<>();
            customMetadata.put("tipo", "classification"); // Marca como arquivo de classificação

            if (categoria != null && !categoria.isBlank()) {
                customMetadata.put("categoria", categoria);
            }
            if (modulo != null && !modulo.isBlank()) {
                customMetadata.put("modulo", modulo);
            }
            if (tags != null && !tags.isBlank()) {
                customMetadata.put("tags", tags);
            }
            if (descricao != null && !descricao.isBlank()) {
                customMetadata.put("descricao", descricao);
            }

            // Upload para o Classification Store
            String resultName = googleFileSearchService.uploadFileWithMetadataToClassification(
                    displayName,
                    file.getBytes(),
                    contentType,
                    customMetadata);

            return ResponseEntity.ok(Map.of(
                    "message", "File uploaded to Classification Store",
                    "store", "ResumoChat_Classification_v2",
                    "id", resultName,
                    "displayName", displayName,
                    "metadata", customMetadata));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Error uploading to Classification Store: " + e.getMessage());
        }
    }

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
            var classFiles = googleFileSearchService.listClassificationFiles();
            var manualFiles = googleFileSearchService.listFiles(googleFileSearchService.getManualsStoreId());

            return ResponseEntity.ok(Map.of(
                    "classificationStore", classFiles,
                    "manualsStore", manualFiles,
                    "classificationCount", classFiles.size(),
                    "manualsCount", manualFiles.size()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Erro ao listar arquivos: " + e.getMessage()));
        }
    }

    @GetMapping("/store-info")
    public ResponseEntity<?> getStoreInfo() {
        try {
            org.json.JSONObject classInfo = googleFileSearchService
                    .getStoreInfo(googleFileSearchService.getClassificationStoreId());
            org.json.JSONObject manualInfo = googleFileSearchService
                    .getStoreInfo(googleFileSearchService.getManualsStoreId());

            return ResponseEntity.ok(Map.of(
                    "classificationStore", classInfo != null ? classInfo.toMap() : "Not found",
                    "manualsStore", manualInfo != null ? manualInfo.toMap() : "Not found"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Erro ao obter informações do store: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFile(@PathVariable String id) {
        boolean deleted = googleFileSearchService.deleteFile(id);
        if (deleted) {
            return ResponseEntity.ok(Map.of("message", "File deleted successfully"));
        } else {
            return ResponseEntity.internalServerError().body("Failed to delete file");
        }
    }

    @PostMapping("/reset")
    public ResponseEntity<?> resetStore() {
        try {
            System.out.println("⚠️ Recebido comando de RESET de base via API");
            boolean deleted = googleFileSearchService.deleteStores();
            if (deleted) {
                return ResponseEntity.ok(Map.of(
                        "status", "success",
                        "message", "All File Search Stores deleted. Please RESTART the application to recreate them.",
                        "note", "A reinicialização é necessária para que o @PostConstruct recrie os IDs."));
            } else {
                return ResponseEntity.ok(Map.of(
                        "status", "partial_success",
                        "message", "Nenhum store foi encontrado ou deletado (talvez já estivessem vazios)."));
            }
        } catch (Exception e) {
            System.err.println("❌ Erro no reset: " + e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()));
        }
    }
}
