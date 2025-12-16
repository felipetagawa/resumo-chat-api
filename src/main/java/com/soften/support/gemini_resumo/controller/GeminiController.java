package com.soften.support.gemini_resumo.controller;

import com.soften.support.gemini_resumo.service.CalledService;
import com.soften.support.gemini_resumo.service.GeminiService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/gemini")
@CrossOrigin(origins = "*") // ajuste em produção
public class GeminiController {

    private final GeminiService geminiService;
    private final CalledService calledService;

    public GeminiController(GeminiService geminiService, CalledService calledService) {
        this.geminiService = geminiService;
        this.calledService = calledService;
    }

    @PostMapping(value = "/resumir", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> resumirJson(@RequestBody Map<String, Object> body) {
        Object textoObj = body.get("texto");
        if (textoObj == null) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("erro", "Campo 'texto' é obrigatório no body JSON."));
        }
        String texto = textoObj.toString().trim();
        if (texto.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("erro", "Campo 'texto' não pode estar vazio."));
        }

        try {
            String summary = geminiService.generateSummary(texto);
            calledService.SaveCall(summary);

            return ResponseEntity.ok(Map.of("summary", summary));
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("erro", e.getMessage()));
        }
    }

    @PostMapping(value = "/resumir", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> resumirPlainText(@RequestBody String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("erro", "Body não pode estar vazio."));
        }
        try {
            String resumo = geminiService.generateSummary(texto.trim());
            calledService.SaveCall(resumo);

            return ResponseEntity.ok(Map.of("summary", resumo));
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("erro", e.getMessage()));
        }
    }

    @GetMapping("/ping")
    public ResponseEntity<?> ping() {
        return ResponseEntity.ok(Map.of("status", "ok", "app", "gemini-summary"));
    }

    @PostMapping(value = "/documentacoes", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> buscarDocumentacoes(@RequestBody Map<String, Object> body) {
        Object resumoObj = body.get("resumo");
        if (resumoObj == null) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("erro", "Campo 'resumo' é obrigatório no body JSON."));
        }
        String resumo = resumoObj.toString().trim();
        if (resumo.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("erro", "Campo 'resumo' não pode estar vazio."));
        }

        try {
            java.util.List<Map<String, Object>> documentacoes = geminiService.buscarDocumentacoes(resumo);
            return ResponseEntity.ok(Map.of("documentacoesSugeridas", documentacoes));
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("erro", e.getMessage()));
        }
    }

    @PostMapping(value = "/solucoes", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> buscarSolucoes(@RequestBody Map<String, Object> body) {
        Object problemaObj = body.get("problema");
        if (problemaObj == null) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("erro", "Campo 'problema' é obrigatório no body JSON."));
        }
        String problema = problemaObj.toString().trim();
        if (problema.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("erro", "Campo 'problema' não pode estar vazio."));
        }

        try {
            java.util.List<String> solucoes = geminiService.buscarSolucoesSimilares(problema);
            return ResponseEntity.ok(Map.of("solucoesSugeridas", solucoes));
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("erro", e.getMessage()));
        }
    }

    @PostMapping(value = "/salvar", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> salvarResumoManual(@RequestBody Map<String, String> body) {
        String titulo = body.get("titulo");
        String conteudo = body.get("conteudo");

        if (conteudo == null || conteudo.isBlank()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("erro", "Campo 'conteudo' é obrigatório."));
        }

        try {
            geminiService.salvarResumoManual(titulo, conteudo);
            return ResponseEntity.ok(Map.of("message", "Resumo salvo na base de conhecimento."));
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("erro", e.getMessage()));
        }
    }

    @GetMapping("/documentacoes/debug")
    public ResponseEntity<?> debugDocumentacoes(@RequestParam(defaultValue = "impressão") String query) {
        try {
            List<org.springframework.ai.document.Document> docs = geminiService.buscarDocumentacaoOficialSmart(query);

            java.util.List<Map<String, Object>> resultado = new java.util.ArrayList<>();
            for (org.springframework.ai.document.Document doc : docs) {
                resultado.add(Map.of(
                        "id", doc.getId(),
                        "content", doc.getText(),
                        "metadata", doc.getMetadata()));
            }

            return ResponseEntity.ok(Map.of(
                    "query", query,
                    "totalEncontrados", docs.size(),
                    "documentos", resultado));
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("erro", e.getMessage()));
        }
    }
}
