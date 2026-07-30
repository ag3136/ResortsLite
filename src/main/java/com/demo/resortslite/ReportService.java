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
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * ReportService - Cloud-native report generation service using Azure Blob Storage
 * 
 * FIXED cr-java-0062: Replaced all local file system write operations with Azure Blob Storage
 * to ensure data durability and availability across container restarts and scaling events.
 * 
 * All report data is now persisted to Azure Blob Storage containers, eliminating dependency
 * on ephemeral local file systems in containerized environments.
 */
@Service
public class ReportService {

    // FIXED cr-java-0061 & cr-java-0062: Replaced hard-coded file paths with Azure Blob Storage configuration
    // Using environment variables for cloud-native configuration management
    @Value("${azure.storage.connection-string}")
    private String azureStorageConnectionString;

    @Value("${azure.storage.container-name:resort-reports}")
    private String reportContainerName;

    @Value("${azure.storage.backup-container-name:resort-backups}")
    private String backupContainerName;

    @Value("${azure.storage.enabled:true}")
    private boolean azureStorageEnabled;

    // FIXED cr-java-0071: Externalized report download base URL to Azure App Configuration
    // This URL can now be configured via environment variables or Azure App Configuration
    // Example: REPORT_DOWNLOAD_BASE_URL=https://reports.resorts.azure.com/download/
    @Value("${app.report.download.base-url:https://reports.resorts-internal.com:8080/download/}")
    private String reportDownloadBaseUrl;

    // FIXED cr-java-0077: Replaced hard-coded port with Azure App Configuration and environment variables
    // Port is now dynamically assigned by Azure Container Apps or App Service
    // The server.port property is configured via environment variables or Azure App Configuration
    @Value("${server.port:8080}")
    private int serverPort;

    private BlobServiceClient blobServiceClient;
    private BlobContainerClient reportContainerClient;
    private BlobContainerClient backupContainerClient;

    /**
     * Initialize Azure Blob Storage clients and containers
     * Creates containers if they don't exist
     */
    @PostConstruct
    public void init() {
        if (azureStorageEnabled && azureStorageConnectionString != null && !azureStorageConnectionString.isEmpty()) {
            try {
                // Initialize Azure Blob Storage client
                blobServiceClient = new BlobServiceClientBuilder()
                        .connectionString(azureStorageConnectionString)
                        .buildClient();

                // Get or create container clients
                reportContainerClient = blobServiceClient.getBlobContainerClient(reportContainerName);
                if (!reportContainerClient.exists()) {
                    reportContainerClient.create();
                }

                backupContainerClient = blobServiceClient.getBlobContainerClient(backupContainerName);
                if (!backupContainerClient.exists()) {
                    backupContainerClient.create();
                }
            } catch (Exception e) {
                System.err.println("Failed to initialize Azure Blob Storage: " + e.getMessage());
                azureStorageEnabled = false;
            }
        }
    }

    /**
     * Generate monthly report and persist to Azure Blob Storage
     * 
     * FIXED cr-java-0062 (Line 42): Replaced FileWriter with Azure Blob Storage upload
     * - Original code used FileWriter to write to local file system (/var/legacy/reports/)
     * - New implementation writes to in-memory buffer and uploads to Azure Blob Storage
     * - Ensures data persistence across container restarts and scaling events
     * 
     * @param month Report month
     * @param year Report year
     * @return Map containing report generation status and storage location
     */
    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        Map<String, Object> result = new HashMap<>();

        try {
            // FIXED cr-java-0062: Generate CSV content in memory instead of writing to local file system
            // Original line 42 had: FileWriter writer = new FileWriter(fullPath);
            // New implementation uses ByteArrayOutputStream for in-memory operations
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
            
            // Write CSV data to in-memory buffer
            writer.write("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
            writer.write("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
            writer.write("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");
            writer.flush();
            writer.close();

            byte[] reportData = outputStream.toByteArray();

            if (azureStorageEnabled && reportContainerClient != null) {
                // FIXED cr-java-0062: Upload to Azure Blob Storage instead of local file system
                // This ensures data durability and availability in cloud environments
                BlobClient blobClient = reportContainerClient.getBlobClient(fileName);
                ByteArrayInputStream inputStream = new ByteArrayInputStream(reportData);
                blobClient.upload(inputStream, reportData.length, true);

                result.put("status", "generated");
                result.put("path", "azure://" + reportContainerName + "/" + fileName);
                result.put("blobUrl", blobClient.getBlobUrl());
                result.put("storageType", "Azure Blob Storage");
                result.put("size", reportData.length);
            } else {
                // Fallback for local development or when Azure Storage is not configured
                result.put("status", "generated");
                result.put("path", "in-memory://" + fileName);
                result.put("size", reportData.length);
                result.put("storageType", "In-Memory (Azure Storage not configured)");
                result.put("warning", "Azure Blob Storage not configured. Data will not persist.");
            }
            
            result.put("serverPort", serverPort); // FIXED cr-java-0077

        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    /**
     * Build report download URL
     * 
     * VIOLATION [Code Sustainability / Medium]: No JavaDoc or method documentation.
     * Missing documentation is flagged across all public methods in the codebase.
     * This increases onboarding time and transformation risk for automated tools.
     * 
     * @param reportName Name of the report file
     * @return Download URL for the report
     */
    public String buildReportDownloadUrl(String reportName) { // doc-missing-001
        // FIXED cr-java-0071: Replaced hard-coded URL with externalized configuration
        // URL is now loaded from Azure App Configuration or environment variables
        return reportDownloadBaseUrl + reportName; // cr-java-0071 FIXED
    }

    /**
     * Get system information including storage configuration
     * 
     * FIXED cr-java-0061 & cr-java-0062: Returns Azure Blob Storage container references
     * instead of local file system paths
     * 
     * @return Map containing system information and storage configuration
     */
    public Map<String, Object> getSystemInfo() { // doc-missing-001
        // FIXED cr-java-0111: Replaced SimpleDateFormat/Date with ZonedDateTime using UTC timezone
        // This ensures consistent timestamp generation across distributed cloud environments
        // regardless of container/server local timezone settings
        String timestamp = ZonedDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        Map<String, Object> info = new HashMap<>();
        
        // FIXED cr-java-0061 & cr-java-0062: Replaced hard-coded paths with Azure Blob Storage container references
        // Original code returned: /var/legacy/reports/ and C:\ResortBackups\nightly\
        // New implementation returns Azure Blob Storage container URIs
        if (azureStorageEnabled && reportContainerClient != null) {
            info.put("reportPath", "azure://" + reportContainerName);
            info.put("backupPath", "azure://" + backupContainerName);
            info.put("storageType", "Azure Blob Storage");
            info.put("reportContainerExists", reportContainerClient.exists());
            info.put("backupContainerExists", backupContainerClient.exists());
        } else {
            info.put("reportPath", "Azure Storage not configured");
            info.put("backupPath", "Azure Storage not configured");
            info.put("storageType", "In-Memory/Local");
            info.put("warning", "Azure Blob Storage not configured. Configure AZURE_STORAGE_CONNECTION_STRING environment variable.");
        }
        
        info.put("serverPort", serverPort);        // FIXED cr-java-0077
        info.put("generatedAt", timestamp);
        return info;
    }
}
