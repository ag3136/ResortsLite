package com.demo.resortslite;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * ReportService - Cloud-native report generation service using Azure Blob Storage
 * 
 * FIXED cr-java-0063: Migrated from java.io.File to Azure Blob Storage for cloud compatibility
 * - Line 37: Replaced File reportDir = new File(REPORT_BASE_PATH) with Azure Blob Storage client
 * - Line 39: Replaced reportDir.mkdirs() with Azure Blob Storage container creation
 * - Line 42: Replaced FileWriter with in-memory ByteArrayOutputStream and Azure Blob upload
 */
@Service
public class ReportService {

    // FIXED cr-java-0061: Replaced hard-coded file paths with Azure Blob Storage configuration
    @Value("${azure.storage.connection-string}")
    private String azureStorageConnectionString;

    @Value("${azure.storage.container-name}")
    private String containerName;

    // FIXED cr-java-0071: Externalized report download base URL to configuration
    // In production, this should be loaded from Azure App Configuration
    @Value("${app.report.download.base-url}")
    private String reportDownloadBaseUrl;

    private BlobServiceClient blobServiceClient;
    private BlobContainerClient containerClient;

    // FIXED cr-java-0077: Replaced hard-coded port with environment variable configuration
    // Azure container orchestration platforms dynamically assign ports. Using environment variable
    // enables dynamic port binding required for Azure Container Apps and AKS deployment.
    @Value("${server.port:8080}")
    private int serverPort; // FIXED cr-java-0077: Externalized to environment variable

    /**
     * Initialize Azure Blob Storage client on service startup
     * Creates container if it doesn't exist
     */
    @PostConstruct
    public void init() {
        // Initialize Azure Blob Storage client
        if (azureStorageConnectionString != null && !azureStorageConnectionString.isEmpty()) {
            try {
                blobServiceClient = new BlobServiceClientBuilder()
                        .connectionString(azureStorageConnectionString)
                        .buildClient();
                
                // Get or create container (FIXED cr-java-0063 Line 39: Replaced reportDir.mkdirs())
                containerClient = blobServiceClient.getBlobContainerClient(containerName);
                if (!containerClient.exists()) {
                    containerClient.create();
                }
            } catch (Exception e) {
                System.err.println("Warning: Failed to initialize Azure Blob Storage: " + e.getMessage());
                System.err.println("Reports will not be persisted. Set AZURE_STORAGE_CONNECTION_STRING environment variable.");
            }
        } else {
            System.err.println("Warning: Azure Blob Storage not configured. Set AZURE_STORAGE_CONNECTION_STRING environment variable.");
        }
    }

    /**
     * Generate monthly report and store in Azure Blob Storage
     * 
     * FIXED cr-java-0063: Replaced java.io.File operations with Azure Blob Storage
     * - No longer uses File API for directory creation (Line 37)
     * - No longer uses FileWriter for local file writing (Line 42)
     * - Uses in-memory ByteArrayOutputStream and uploads to Azure Blob Storage
     * 
     * @param month Report month
     * @param year Report year
     * @return Map containing report generation status and blob URL
     */
    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        
        Map<String, Object> result = new HashMap<>();

        try {
            // FIXED cr-java-0063 Line 42: Replaced FileWriter with in-memory stream
            // Create CSV content in memory instead of writing to local file system
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
            
            // Generate CSV content
            writer.write("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
            writer.write("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
            writer.write("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");
            writer.flush();
            writer.close();

            // Upload to Azure Blob Storage (cloud-native persistent storage)
            if (containerClient != null) {
                BlobClient blobClient = containerClient.getBlobClient(fileName);
                byte[] data = outputStream.toByteArray();
                ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
                blobClient.upload(inputStream, data.length, true);
                
                result.put("status", "generated");
                result.put("path", "azure-blob://" + containerName + "/" + fileName);
                result.put("blobUrl", blobClient.getBlobUrl());
                result.put("sizeBytes", data.length);
            } else {
                result.put("status", "error");
                result.put("message", "Azure Blob Storage not configured. Set AZURE_STORAGE_CONNECTION_STRING environment variable.");
            }
            result.put("serverPort", serverPort); // FIXED cr-java-0077

        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", "Failed to generate report: " + e.getMessage());
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Failed to upload report to Azure Blob Storage: " + e.getMessage());
        }

        return result;
    }

    // VIOLATION [Code Sustainability / Medium]: No JavaDoc or method documentation.
    // Missing documentation is flagged across all public methods in the codebase.
    // This increases onboarding time and transformation risk for automated tools.
    public String buildReportDownloadUrl(String reportName) { // doc-missing-001
        // FIXED cr-java-0071: Replaced hard-coded URL with externalized configuration
        // URL is now loaded from application.properties via @Value annotation
        // In production, migrate to Azure App Configuration for centralized config management
        // VIOLATION cr-java-0088 [Cloud Compatibility / Mandatory]: Plain HTTP URL
        // hardcoded for report download. Cloud security standards enforce HTTPS.
        return reportDownloadBaseUrl + "/download/" + reportName; // cr-java-0088 (FIXED cr-java-0071)
    }

    /**
     * Get system information including Azure Blob Storage configuration
     * 
     * FIXED cr-java-0063: Returns Azure Blob Storage paths instead of local file paths
     * 
     * @return Map containing system information
     */
    public Map<String, Object> getSystemInfo() { // doc-missing-001
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        Map<String, Object> info = new HashMap<>();
        // FIXED cr-java-0063: Replaced hard-coded REPORT_BASE_PATH with Azure Blob Storage info
        info.put("reportPath", "azure-blob://" + containerName);
        info.put("backupPath", "azure-blob://" + containerName + "/backups");
        info.put("storageType", "Azure Blob Storage");
        info.put("containerName", containerName);
        info.put("storageConfigured", containerClient != null);
        info.put("serverPort", serverPort);        // FIXED cr-java-0077
        info.put("generatedAt", timestamp);
        return info;
    }
}
