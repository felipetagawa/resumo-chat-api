package com.soften.support.gemini_resumo.config;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class CustomGeminiEmbeddingModel extends AbstractEmbeddingModel {

    @Value("${gemini.api.key}")
    private String apiKey;

    private final RestClient restClient;

    public CustomGeminiEmbeddingModel() {
        this.restClient = RestClient.builder().build();
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        int index = 0;

        for (String input : request.getInstructions()) {
            List<Double> vector = generateEmbedding(input);
            if (vector != null) {
                float[] fVector = new float[vector.size()];
                for (int i = 0; i < vector.size(); i++) {
                    fVector[i] = vector.get(i).floatValue();
                }
                embeddings.add(new Embedding(fVector, index++));
            }
        }

        // Return a dummy Usage object to satisfy any strict checks, though our manual
        // model doesn't enforce it
        return new EmbeddingResponse(embeddings);
    }

    private List<Double> generateEmbedding(String text) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/text-embedding-004:embedContent?key="
                + apiKey;

        // Simple JSON payload construction
        // "content": { "parts": [{ "text": "..." }] }
        // We need to escape the text carefully or use a Map

        Map<String, Object> payload = Map.of(
                "content", Map.of(
                        "parts", List.of(Map.of("text", text))));

        try {
            GeminiEmbeddingResponse response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(GeminiEmbeddingResponse.class);

            if (response != null && response.embedding() != null) {
                return response.embedding().values();
            }
        } catch (Exception e) {
            System.err.println("Erro ao gerar embedding para texto: " + text + ". Erro: " + e.getMessage());
            e.printStackTrace();
        }
        return new ArrayList<>(); // Return empty list on failure
    }

    // Record for simple JSON mapping of response
    // { "embedding": { "values": [0.1, 0.2, ...] } }
    record GeminiEmbeddingResponse(EmbeddingData embedding) {
    }

    record EmbeddingData(List<Double> values) {
    }

    @Override
    public float[] embed(Document document) {
        return this.embed(document.getText());
    }
}
