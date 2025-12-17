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

            // Upload com metadados se fornecidos, senão upload simples
            String resultName;
            if (!customMetadata.isEmpty()) {
                resultName = googleFileSearchService.uploadFileWithMetadata(
                        originalFilename,
                        file.getBytes(),
                        contentType,
                        customMetadata);
            } else {
                resultName = googleFileSearchService.uploadFile(
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

            // Adiciona informações do store para contexto
            org.json.JSONObject storeInfo = googleFileSearchService.getStoreInfo();
            int documentsInStore = storeInfo != null ? storeInfo.optInt("activeDocumentsCount", 0) : 0;

            return ResponseEntity.ok(Map.of(
                    "warning",
                    "Este endpoint lista arquivos da Files API (upload simples), não documentos do FileSearchStore",
                    "suggestion", "Use GET /api/docs/store-info para ver informações dos documentos no FileSearchStore",
                    "filesApiCount", files.size(),
                    "filesApiList", files,
                    "fileSearchStoreDocuments", documentsInStore,
                    "note",
                    "Você tem " + documentsInStore + " documentos ativos no FileSearchStore que não aparecem aqui"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Erro ao listar arquivos: " + e.getMessage()));
        }
    }

    @GetMapping("/store-info")
    public ResponseEntity<?> getStoreInfo() {
        try {
            org.json.JSONObject storeInfo = googleFileSearchService.getStoreInfo();

            if (storeInfo == null) {
                return ResponseEntity.internalServerError()
                        .body(Map.of("error", "Não foi possível obter informações do store"));
            }

            // Converte JSONObject para Map para retornar como JSON
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("name", storeInfo.optString("name", "N/A"));
            response.put("displayName", storeInfo.optString("displayName", "N/A"));
            response.put("activeDocumentsCount", storeInfo.optInt("activeDocumentsCount", 0));
            response.put("pendingDocumentsCount", storeInfo.optInt("pendingDocumentsCount", 0));
            response.put("failedDocumentsCount", storeInfo.optInt("failedDocumentsCount", 0));
            response.put("sizeBytes", storeInfo.optLong("sizeBytes", 0));
            response.put("createTime", storeInfo.optString("createTime", "N/A"));
            response.put("updateTime", storeInfo.optString("updateTime", "N/A"));

            return ResponseEntity.ok(response);
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

    @DeleteMapping("/reset")
    public ResponseEntity<?> resetStore() {
        boolean deleted = googleFileSearchService.deleteStore();
        if (deleted) {
            return ResponseEntity
                    .ok(Map.of("message", "File Search Store deleted. A new one will be created on next upload."));
        } else {
            return ResponseEntity.internalServerError().body("Failed to delete store (maybe it doesn't exist?)");
        }
    }
}
