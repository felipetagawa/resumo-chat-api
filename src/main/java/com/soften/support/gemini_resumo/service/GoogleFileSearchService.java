package com.soften.support.gemini_resumo.service;

import jakarta.annotation.PostConstruct;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

@Service
public class GoogleFileSearchService {

    @Value("${gemini.api.key:}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    private static final String UPLOAD_URL = "https://generativelanguage.googleapis.com/upload/v1beta";
    private static final String STORE_DISPLAY_NAME = "ResumoChat_Docs";

    private String currentStoreId;

    @PostConstruct
    public void init() {
        if (apiKey != null && !apiKey.isBlank()) {
            ensureStoreExists();
        }
    }

    private void ensureStoreExists() {
        try {
            // 1. List existing stores to find ours
            String listUrl = BASE_URL + "/fileSearchStores?key=" + apiKey;
            ResponseEntity<String> response = restTemplate.getForEntity(listUrl, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JSONObject json = new JSONObject(response.getBody());
                if (json.has("fileSearchStores")) {
                    JSONArray stores = json.getJSONArray("fileSearchStores");
                    for (int i = 0; i < stores.length(); i++) {
                        JSONObject store = stores.getJSONObject(i);
                        if (store.optString("displayName").equals(STORE_DISPLAY_NAME)) {
                            this.currentStoreId = store.getString("name"); // format: fileSearchStores/xyz
                            System.out.println("✅ Found existing File Search Store: " + this.currentStoreId);
                            return;
                        }
                    }
                }
            }

            // 2. If not found, create new one
            System.out.println("⚠️ Store not found. Creating new File Search Store: " + STORE_DISPLAY_NAME);
            createStore();

        } catch (Exception e) {
            System.err.println("❌ Error ensuring File Search Store exists: " + e.getMessage());
        }
    }

    private void createStore() {
        String createUrl = BASE_URL + "/fileSearchStores?key=" + apiKey;

        JSONObject body = new JSONObject();
        body.put("displayName", STORE_DISPLAY_NAME);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(createUrl, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                JSONObject json = new JSONObject(response.getBody());
                this.currentStoreId = json.getString("name");
                System.out.println("✅ Expected Store created: " + this.currentStoreId);
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to create store: " + e.getMessage());
        }
    }

    public String uploadFile(String displayName, byte[] fileContent, String mimeType) {
        if (currentStoreId == null) {
            ensureStoreExists();
            if (currentStoreId == null)
                throw new RuntimeException("File Search Store is not available.");
        }

        // Endpoint: POST .../{storeName}:uploadToFileSearchStore
        String uploadUrl = UPLOAD_URL + "/" + currentStoreId + ":uploadToFileSearchStore?key=" + apiKey;

        try {
            // We need to send a multipart request:
            // Part 1: Metadata (MIME type application/json)
            // Part 2: File Content (MIME type of the file)

            JSONObject metadata = new JSONObject();
            metadata.put("displayName", displayName);

            HttpHeaders jsonHeaders = new HttpHeaders();
            jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> jsonPart = new HttpEntity<>(metadata.toString(), jsonHeaders);

            HttpHeaders fileHeaders = new HttpHeaders();
            try {
                fileHeaders.setContentType(MediaType.parseMediaType(mimeType));
            } catch (Exception e) {
                fileHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            }

            // Create a ByteArrayResource with a filename so RestTemplate treats it as a
            // file upload
            ByteArrayResource fileResource = new ByteArrayResource(fileContent) {
                @Override
                public String getFilename() {
                    return displayName != null ? displayName : "file";
                }
            };
            HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(fileResource, fileHeaders);

            MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
            parts.add("metadata", jsonPart);
            parts.add("file", filePart);

            HttpHeaders mainHeaders = new HttpHeaders();
            mainHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(parts, mainHeaders);

            ResponseEntity<String> response = restTemplate.postForEntity(uploadUrl, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JSONObject responseJson = new JSONObject(response.getBody());
                if (responseJson.has("response")) {
                    return responseJson.getJSONObject("response").optString("name");
                }
                return "Upload initiated"; // Async operation
            }

            throw new RuntimeException("Upload failed with status: " + response.getStatusCode());

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error uploading document: " + e.getMessage());
        }
    }

    // Overload for backward compatibility (Text/Plain) if needed, or just update
    // controller
    public String uploadDocument(String title, String content) {
        return uploadFile(title, content.getBytes(StandardCharsets.UTF_8), "text/plain");
    }

    public boolean fileExists(String displayName) {
        try {
            // Basic implementation: list recent files and check names.
            // Note: heavily paginated in real apps, but sufficient for this specific set of
            // static files.
            String listUrl = BASE_URL + "/files?key=" + apiKey;
            ResponseEntity<String> response = restTemplate.getForEntity(listUrl, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JSONObject json = new JSONObject(response.getBody());
                if (json.has("files")) {
                    JSONArray files = json.getJSONArray("files");
                    for (int i = 0; i < files.length(); i++) {
                        if (files.getJSONObject(i).optString("displayName").equals(displayName)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not list files to check existence: " + e.getMessage());
        }
        return false;
    }

    public String simpleSearch(String query) {
        return simpleSearch(query, null);
    }

    public String simpleSearch(String query, String systemInstruction) {
        if (currentStoreId == null) {
            ensureStoreExists();
        }

        String generateUrl = BASE_URL + "/models/gemini-2.5-flash:generateContent?key=" + apiKey;

        try {
            JSONObject body = new JSONObject();

            if (systemInstruction != null && !systemInstruction.isBlank()) {
                JSONObject systemInstructionObj = new JSONObject();
                systemInstructionObj.put("role", "system");
                systemInstructionObj.put("parts", new JSONArray().put(new JSONObject().put("text", systemInstruction)));
                body.put("system_instruction", systemInstructionObj);
            }

            // Contents
            JSONArray contents = new JSONArray();
            JSONObject contentMsg = new JSONObject();
            contentMsg.put("role", "user");
            contentMsg.put("parts", new JSONArray().put(new JSONObject().put("text", query)));
            contents.put(contentMsg);
            body.put("contents", contents);

            // Tools (File Seach)
            JSONObject fileSearchTool = new JSONObject();
            JSONObject fileSearchObj = new JSONObject();
            fileSearchObj.put("fileSearchStoreNames", new JSONArray().put(currentStoreId));
            fileSearchTool.put("fileSearch", fileSearchObj);

            body.put("tools", new JSONArray().put(fileSearchTool));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);

            ResponseEntity<String> response = restTemplate.postForEntity(generateUrl, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JSONObject json = new JSONObject(response.getBody());
                // Extract text
                return json.getJSONArray("candidates")
                        .getJSONObject(0).getJSONObject("content")
                        .getJSONArray("parts").getJSONObject(0).getString("text");
            }

            return "Error: " + response.getStatusCode();

        } catch (Exception e) {
            return "Error searching: " + e.getMessage();
        }
    }
}
