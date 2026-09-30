package com.soften.support.gemini_resumo.service;

import com.soften.support.gemini_resumo.config.GeminiApiProperties;
import jakarta.annotation.PostConstruct;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class GoogleFileSearchService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final GeminiApiProperties properties;

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    private static final String UPLOAD_URL = "https://generativelanguage.googleapis.com/upload/v1beta";
    private static final String CLASSIFICATION_STORE_NAME = "ResumoChat_Classification_v2";
    private static final String MANUALS_STORE_NAME = "ResumoChat_Manuals_v2";

    public GoogleFileSearchService(GeminiApiProperties properties) {
        this.properties = properties;
    }

    public boolean deleteStores() {
        try {
            System.out.println("☢️ INICIANDO LIMPEZA NUCLEAR DA BASE GOOGLE...");

            String listStoresUrl = BASE_URL + "/fileSearchStores?key=" + properties.getKey();
            ResponseEntity<String> storeResponse = restTemplate.getForEntity(listStoresUrl, String.class);
            if (storeResponse.getStatusCode().is2xxSuccessful() && storeResponse.getBody() != null) {
                JSONObject json = new JSONObject(storeResponse.getBody());
                if (json.has("fileSearchStores")) {
                    JSONArray stores = json.getJSONArray("fileSearchStores");
                    for (int i = 0; i < stores.length(); i++) {
                        JSONObject store = stores.getJSONObject(i);
                        String dName = store.optString("displayName", "");
                        String sName = store.getString("name");

                        if (dName.contains("ResumoChat_")) {
                            System.out.println("📋 Processando Store: [" + dName + "] (" + sName + ")");

                            int deletedFiles = deleteAllFilesFromStore(sName);
                            System.out.println("  ✅ Deletados " + deletedFiles + " arquivo(s) do store");

                            System.out.println("  🗑️ Removendo Store vazio: [" + dName + "]");
                            deleteSpecificStore(sName);
                        }
                    }
                }
            }

            String listFilesUrl = BASE_URL + "/files?key=" + properties.getKey();
            ResponseEntity<String> fileResponse = restTemplate.getForEntity(listFilesUrl, String.class);
            if (fileResponse.getStatusCode().is2xxSuccessful() && fileResponse.getBody() != null) {
                JSONObject json = new JSONObject(fileResponse.getBody());
                if (json.has("files")) {
                    JSONArray files = json.getJSONArray("files");
                    int orphanCount = 0;
                    for (int i = 0; i < files.length(); i++) {
                        JSONObject file = files.getJSONObject(i);
                        String dName = file.optString("displayName", "");
                        String fName = file.getString("name");

                        if (dName.startsWith("CLASS_") || dName.contains(".txt")) {
                            System.out.println("🗑️ Removendo Arquivo Órfão: " + dName);
                            deleteFile(fName);
                            orphanCount++;
                        }
                    }
                    if (orphanCount > 0) {
                        System.out.println("✅ Deletados " + orphanCount + " arquivo(s) órfão(s)");
                    }
                }
            }

            this.classificationStoreId = null;
            this.manualsStoreId = null;
            System.out.println("✨ LIMPEZA NUCLEAR CONCLUÍDA COM SUCESSO.");
            return true;
        } catch (Exception e) {
            System.err.println("❌ Erro durante a limpeza total: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private int deleteAllFilesFromStore(String storeId) {
        try {
            String listFilesUrl = BASE_URL + "/files?key=" + properties.getKey();
            ResponseEntity<String> response = restTemplate.getForEntity(listFilesUrl, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JSONObject json = new JSONObject(response.getBody());
                int deletedCount = 0;

                if (json.has("files")) {
                    JSONArray files = json.getJSONArray("files");

                    for (int i = 0; i < files.length(); i++) {
                        JSONObject file = files.getJSONObject(i);
                        String fileName = file.getString("name");
                        String displayName = file.optString("displayName", "");

                        System.out.println("    🗑️ Deletando arquivo: " + displayName + " (" + fileName + ")");
                        if (deleteFile(fileName)) {
                            deletedCount++;
                        }
                    }
                }

                return deletedCount;
            }
        } catch (Exception e) {
            System.err.println("⚠️ Erro ao deletar arquivos do store " + storeId + ": " + e.getMessage());
        }
        return 0;
    }

    private boolean deleteSpecificStore(String storeId) {
        if (storeId == null)
            return false;
        try {
            System.out.println("🔄 Deletando Store: " + storeId);
            String deleteUrl = BASE_URL + "/" + storeId + "?key=" + properties.getKey();
            restTemplate.delete(deleteUrl);
            return true;
        } catch (Exception e) {
            System.err.println("❌ Erro ao deletar store " + storeId + ": " + e.getMessage());
            return false;
        }
    }

    private String classificationStoreId;
    private String manualsStoreId;

    @PostConstruct
    public void init() {
        if (properties.getKey() != null && !properties.getKey().isBlank()) {
            // Run initialization in a separate thread to avoid blocking application startup
            new Thread(() -> {
                try {
                    System.out.println("🚀 Starting Google File Search Service initialization in background...");
                    this.classificationStoreId = ensureStoreExists(CLASSIFICATION_STORE_NAME);
                    this.manualsStoreId = ensureStoreExists(MANUALS_STORE_NAME);
                    if (this.classificationStoreId != null && this.manualsStoreId != null) {
                        System.out.println("✅ Google File Search Service initialized successfully.");
                    } else {
                        System.err.println("⚠️ Google File Search Service initialization completed with missing stores.");
                    }
                } catch (Exception e) {
                    System.err.println("❌ Failed to initialize Google File Search Service: " + e.getMessage());
                    e.printStackTrace();
                }
            }).start();
        }
    }

    private String ensureStoreExists(String displayName) {
        try {
            String listUrl = BASE_URL + "/fileSearchStores?key=" + properties.getKey();
            ResponseEntity<String> response = restTemplate.getForEntity(listUrl, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JSONObject json = new JSONObject(response.getBody());
                if (json.has("fileSearchStores")) {
                    JSONArray stores = json.getJSONArray("fileSearchStores");
                    for (int i = 0; i < stores.length(); i++) {
                        JSONObject store = stores.getJSONObject(i);
                        if (store.optString("displayName").equals(displayName)) {
                            String storeId = store.getString("name");
                            System.out.println("✅ Found existing File Search Store [" + displayName + "]: " + storeId);
                            return storeId;
                        }
                    }
                }
            }

            System.out.println("🔨 Creating new File Search Store: " + displayName);
            String createUrl = BASE_URL + "/fileSearchStores?key=" + properties.getKey();
            JSONObject createBody = new JSONObject();
            createBody.put("displayName", displayName);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> createEntity = new HttpEntity<>(createBody.toString(), headers);

            ResponseEntity<String> createResponse = restTemplate.postForEntity(createUrl, createEntity, String.class);
            if (createResponse.getStatusCode().is2xxSuccessful() && createResponse.getBody() != null) {
                JSONObject createJson = new JSONObject(createResponse.getBody());
                String storeId = createJson.getString("name");
                System.out.println("✨ Created File Search Store [" + displayName + "]: " + storeId);
                return storeId;
            }

        } catch (Exception e) {
            System.err.println("❌ Error ensuring store exists [" + displayName + "]: " + e.getMessage());
        }
        return null;
    }

    public String getClassificationStoreId() {
        return classificationStoreId;
    }

    public String getManualsStoreId() {
        return manualsStoreId;
    }

    public String uploadFileToClassification(String displayName, byte[] content, String mimeType) {
        return uploadFile(displayName, content, mimeType, classificationStoreId);
    }

    public String uploadFileToManuals(String displayName, byte[] content, String mimeType) {
        return uploadFile(displayName, content, mimeType, manualsStoreId);
    }

    public String uploadFile(String displayName, byte[] content, String mimeType, String storeId) {
        if (storeId == null) {
            System.err.println("❌ Target Store ID is null. Cannot upload.");
            return null;
        }

        String uploadUrl = UPLOAD_URL + "/" + storeId + ":uploadToFileSearchStore?key=" + properties.getKey();

        try {
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

            ByteArrayResource fileResource = new ByteArrayResource(content) {
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
                return "Upload initiated";
            }

            throw new RuntimeException("Upload failed with status: " + response.getStatusCode());

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error uploading document: " + e.getMessage());
        }
    }

    public String uploadDocument(String title, String content) {
        return uploadFile(title, content.getBytes(StandardCharsets.UTF_8), "text/plain", classificationStoreId);
    }

    public String uploadFileWithMetadata(String displayName, byte[] fileContent, String mimeType,
            Map<String, String> customMetadata, String storeId) {
        if (storeId == null) {
            System.err.println("❌ Target Store ID is null. Cannot upload with metadata.");
            throw new RuntimeException("File Search Store is not available.");
        }

        String uploadUrl = UPLOAD_URL + "/" + storeId + ":uploadToFileSearchStore?key=" + properties.getKey();

        try {
            JSONObject metadata = new JSONObject();
            metadata.put("displayName", displayName);

            if (customMetadata != null && !customMetadata.isEmpty()) {
                JSONArray customMetadataArray = new JSONArray();
                for (Map.Entry<String, String> entry : customMetadata.entrySet()) {
                    JSONObject metadataItem = new JSONObject();
                    metadataItem.put("key", entry.getKey());
                    metadataItem.put("stringValue", entry.getValue());
                    customMetadataArray.put(metadataItem);
                }
                metadata.put("customMetadata", customMetadataArray);
            }

            HttpHeaders jsonHeaders = new HttpHeaders();
            jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> jsonPart = new HttpEntity<>(metadata.toString(), jsonHeaders);

            HttpHeaders fileHeaders = new HttpHeaders();
            try {
                fileHeaders.setContentType(MediaType.parseMediaType(mimeType));
            } catch (Exception e) {
                fileHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            }

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

                String operationName = responseJson.optString("name", "Unknown");
                boolean done = responseJson.optBoolean("done", false);

                System.out.println("📤 Upload com metadata:");
                System.out.println("  Arquivo: " + displayName);
                System.out.println("  Metadata: " + customMetadata);
                System.out.println("  Status: " + (done ? "Concluído" : "Processando"));

                if (done && responseJson.has("response")) {
                    return responseJson.getJSONObject("response").optString("name", operationName);
                }

                return operationName;
            }

            throw new RuntimeException("Upload failed with status: " + response.getStatusCode());

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error uploading document with metadata: " + e.getMessage());
        }
    }

    public String uploadFileWithMetadataToClassification(String displayName, byte[] fileContent, String mimeType,
            Map<String, String> customMetadata) {
        return uploadFileWithMetadata(displayName, fileContent, mimeType, customMetadata, classificationStoreId);
    }

    public String uploadFileWithMetadataToManuals(String displayName, byte[] fileContent, String mimeType,
            Map<String, String> customMetadata) {
        return uploadFileWithMetadata(displayName, fileContent, mimeType, customMetadata, manualsStoreId);
    }

    public boolean fileExistsInClassification(String displayName) {
        return fileExists(displayName, classificationStoreId);
    }

    public boolean fileExists(String displayName, String storeId) {
        if (displayName == null || displayName.isBlank()) {
            System.err.println("⚠️ Cannot check file existence: displayName is null or empty");
            return false;
        }

        try {
            System.out.println("🔍 Checking if file exists: " + displayName);

            String url = BASE_URL + "/files?key=" + properties.getKey();
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JSONObject json = new JSONObject(response.getBody());
                if (json.has("files")) {
                    JSONArray files = json.getJSONArray("files");
                    for (int i = 0; i < files.length(); i++) {
                        JSONObject file = files.getJSONObject(i);
                        String existingDisplayName = file.optString("displayName", "");

                        if (displayName.equals(existingDisplayName)) {
                            String state = file.optString("state", "UNKNOWN");
                            System.out.println("  📝 Found file with state: " + state);

                            if ("ACTIVE".equals(state) || "PROCESSING".equals(state)) {
                                System.out.println("  ✅ File exists and is valid: " + displayName);
                                return true;
                            } else if ("FAILED".equals(state)) {
                                System.out.println("  ⚠️ File exists but is in FAILED state: " + displayName);
                                return false;
                            }
                        }
                    }
                }
            }

            System.out.println("  ❌ File NOT found: " + displayName);
            return false;
        } catch (Exception e) {
            System.err.println("⚠️ Erro ao verificar existência do arquivo '" + displayName + "': " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    public java.util.List<java.util.Map<String, Object>> listClassificationFiles() {
        return listFiles(classificationStoreId);
    }

    public java.util.List<java.util.Map<String, Object>> listFiles(String storeId) {
        if (storeId == null)
            return java.util.Collections.emptyList();

        String url = BASE_URL + "/" + storeId + "/files?key=" + properties.getKey();
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JSONObject json = new JSONObject(response.getBody());
                java.util.List<java.util.Map<String, Object>> fileList = new java.util.ArrayList<>();
                if (json.has("files")) {
                    JSONArray files = json.getJSONArray("files");
                    for (int i = 0; i < files.length(); i++) {
                        JSONObject file = files.getJSONObject(i);
                        fileList.add(java.util.Map.of(
                                "name", file.getString("name"),
                                "displayName", file.optString("displayName", "unnamed")));
                    }
                }
                return fileList;
            }
        } catch (Exception e) {
            System.err.println("❌ Erro ao listar arquivos do store " + storeId + ": " + e.getMessage());
        }
        return java.util.Collections.emptyList();
    }

    public String searchClassification(String query, String systemInstruction) {
        return simpleSearch(query, systemInstruction, classificationStoreId);
    }

    public String searchManuals(String query, String systemInstruction) {
        return simpleSearch(query, systemInstruction, manualsStoreId);
    }

    public String simpleSearch(String query, String systemInstruction, String storeId) {
        if (storeId == null) {
            return "Erro: Store ID não inicializado.";
        }

        String generateUrl = BASE_URL + "/models/" + properties.getModel() + ":generateContent?key=" + properties.getKey();

        try {
            JSONObject body = new JSONObject();

            if (systemInstruction != null && !systemInstruction.isBlank()) {
                JSONObject systemInstructionObj = new JSONObject();
                systemInstructionObj.put("role", "system");
                systemInstructionObj.put("parts", new JSONArray().put(new JSONObject().put("text", systemInstruction)));
                body.put("system_instruction", systemInstructionObj);
            }

            JSONArray contents = new JSONArray();
            JSONObject contentMsg = new JSONObject();
            contentMsg.put("role", "user");
            contentMsg.put("parts", new JSONArray().put(new JSONObject().put("text", query)));
            contents.put(contentMsg);
            body.put("contents", contents);

            JSONObject generationConfig = new JSONObject();
            generationConfig.put("temperature", 0.0);
            body.put("generationConfig", generationConfig);

            JSONObject fileSearchTool = new JSONObject();
            JSONObject fileSearchObj = new JSONObject();
            fileSearchObj.put("fileSearchStoreNames", new JSONArray().put(storeId));
            fileSearchTool.put("fileSearch", fileSearchObj);

            body.put("tools", new JSONArray().put(fileSearchTool));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);

            ResponseEntity<String> response = restTemplate.postForEntity(generateUrl, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                String responseBody = response.getBody();
                System.out.println("📥 File Search Response: " + responseBody);

                try {
                    JSONObject json = new JSONObject(responseBody);

                    if (!json.has("candidates") || json.getJSONArray("candidates").length() == 0) {
                        System.err.println("❌ No candidates in response");
                        return "Nenhuma correspondência encontrada na documentação.";
                    }

                    JSONObject candidate = json.getJSONArray("candidates").getJSONObject(0);

                    if (!candidate.has("content")) {
                        System.err.println("❌ No content in candidate");
                        return "Resposta sem conteúdo.";
                    }

                    JSONObject content = candidate.getJSONObject("content");

                    if (!content.has("parts") || content.getJSONArray("parts").length() == 0) {
                        System.err.println("❌ No parts in content");
                        return "Resposta sem partes de texto.";
                    }

                    JSONObject firstPart = content.getJSONArray("parts").getJSONObject(0);

                    if (firstPart.has("text")) {
                        return firstPart.getString("text");
                    } else {
                        System.err.println("❌ No text in first part: " + firstPart.toString());
                        return "Resposta sem texto.";
                    }

                } catch (Exception parseException) {
                    System.err.println("❌ Error parsing response: " + parseException.getMessage());
                    System.err.println("Response was: " + responseBody);
                    return "Erro ao processar resposta: " + parseException.getMessage();
                }
            }

            return "Error: " + response.getStatusCode();

        } catch (Exception e) {
            e.printStackTrace();
            return "Error searching: " + e.getMessage();
        }
    }

    public JSONObject getStoreInfo(String storeId) {
        if (storeId == null) {
            System.err.println("❌ Store ID não disponível");
            return null;
        }

        String url = BASE_URL + "/" + storeId + "?key=" + properties.getKey();

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JSONObject storeInfo = new JSONObject(response.getBody());

                System.out.println("📊 FileSearchStore Info:");
                System.out.println("  Name: " + storeInfo.optString("name", "N/A"));
                System.out.println("  Display Name: " + storeInfo.optString("displayName", "N/A"));
                System.out.println("  Active Documents: " + storeInfo.optString("activeDocumentsCount", "0"));
                System.out.println("  Pending Documents: " + storeInfo.optString("pendingDocumentsCount", "0"));
                System.out.println("  Failed Documents: " + storeInfo.optString("failedDocumentsCount", "0"));
                System.out.println("  Total Size: " + storeInfo.optString("sizeBytes", "0") + " bytes");
                System.out.println("  Created: " + storeInfo.optString("createTime", "N/A"));
                System.out.println("  Updated: " + storeInfo.optString("updateTime", "N/A"));

                return storeInfo;
            } else {
                System.err.println("❌ Erro ao obter info do store. Status: " + response.getStatusCode());
                return null;
            }

        } catch (Exception e) {
            System.err.println("❌ Erro ao obter informações do store: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public boolean deleteFile(String fileName) {
        if (fileName == null || fileName.isBlank())
            return false;

        try {
            if (!fileName.startsWith("files/")) {
                fileName = "files/" + fileName;
            }

            String deleteUrl = BASE_URL + "/" + fileName + "?key=" + properties.getKey();

            restTemplate.delete(deleteUrl);
            System.out.println("🗑️ Arquivo deletado: " + fileName);
            return true;
        } catch (Exception e) {
            System.err.println("❌ Erro ao deletar arquivo " + fileName + ": " + e.getMessage());
            return false;
        }
    }

    public JSONObject checkOperationStatus(String operationName) {
        if (operationName == null || operationName.isBlank()) {
            System.err.println("❌ Nome da operação não pode ser vazio");
            return null;
        }

        String url = BASE_URL + "/" + operationName + "?key=" + properties.getKey();

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JSONObject operation = new JSONObject(response.getBody());

                boolean done = operation.optBoolean("done", false);
                String name = operation.optString("name", "N/A");

                System.out.println("🔍 Operation Status:");
                System.out.println("  Name: " + name);
                System.out.println("  Done: " + done);

                if (done) {
                    if (operation.has("error")) {
                        JSONObject error = operation.getJSONObject("error");
                        System.out.println("  ❌ Error: " + error.optString("message", "Unknown error"));
                    } else if (operation.has("response")) {
                        JSONObject result = operation.getJSONObject("response");
                        System.out.println("  ✅ Success: " + result.optString("name", "Completed"));
                    }
                } else {
                    System.out.println("  ⏳ Still processing...");
                }

                return operation;
            } else {
                System.err.println("❌ Erro ao verificar operação. Status: " + response.getStatusCode());
                return null;
            }

        } catch (Exception e) {
            System.err.println("❌ Erro ao verificar status da operação: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}
