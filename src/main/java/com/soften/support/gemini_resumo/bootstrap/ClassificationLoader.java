package com.soften.support.gemini_resumo.bootstrap;

import com.soften.support.gemini_resumo.service.GoogleFileSearchService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class ClassificationLoader implements CommandLineRunner {

    private final GoogleFileSearchService googleFileSearchService;
    private final ResourcePatternResolver resourcePatternResolver;

    public ClassificationLoader(GoogleFileSearchService googleFileSearchService,
            ResourcePatternResolver resourcePatternResolver) {
        this.googleFileSearchService = googleFileSearchService;
        this.resourcePatternResolver = resourcePatternResolver;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("🔄 Checking Classification Files in Google File Search...");

        Resource[] resources = resourcePatternResolver.getResources("classpath:documentation_data_part*.txt");

        if (resources.length == 0) {
            System.out.println("⚠️ No classification data files found in classpath.");
            return;
        }

        for (Resource resource : resources) {
            String filename = resource.getFilename();
            // Use a prefix to identify these files easily
            String displayName = "CLASS_" + filename;

            if (googleFileSearchService.fileExists(displayName)) {
                System.out.println("✅ File already exists: " + displayName);
            } else {
                System.out.println("📤 Uploading file: " + displayName);
                try (InputStream inputStream = resource.getInputStream()) {
                    byte[] content = inputStream.readAllBytes();
                    googleFileSearchService.uploadFile(displayName, content, "text/plain");
                    System.out.println("✅ Upload complete: " + displayName);
                } catch (Exception e) {
                    System.err.println("❌ Failed to upload " + displayName + ": " + e.getMessage());
                }
            }
        }
        System.out.println("🏁 Classification Files check complete.");
    }
}
