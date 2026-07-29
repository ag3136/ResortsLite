package com.demo.resortslite;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Cloud-native report service using Azure Blob Storage for persistent file storage
 * and Azure Service Bus for scheduled task execution.
 * 
 * Fixes applied:
 * - cr-java-0061: Replaced hard-coded file paths with Azure Blob Storage
 * - cr-java-0062: Replaced local file writes with Azure Blob Storage
 * - cr-java-0063: Migrated java.io.File operations to Azure Blob Storage
 * - cr-java-0071: Externalized URLs to Azure App Configuration
 * - cr-java-0077: Replaced hard-coded ports with environment variables
 * - cr-java-0111: Replaced java.util.Timer with Azure Service Bus scheduled messages
 */
@Service
public class ReportService {

    @Value("${azure.storage.blob-endpoint}")
    private String blobEndpoint;

    @Value("${azure.storage.container-name}")
    private String containerName;

    @Value("${app.payment.endpoint}")
    private String reportDownloadBaseUrl;

    @Value("${server.port}")
    private String serverPort;

    @Value("${azure.servicebus.connection-string}")
    private String serviceBusConnectionString;

    @Value("${azure.servicebus.queue-name}")
    private String serviceBusQueueName;

    private BlobServiceClient blobServiceClient;
    private BlobContainerClient containerClient;

    /**
     * Initializes Azure Blob Storage client using DefaultAzureCredential for secure authentication.
     * This method is called lazily to avoid initialization issues if Azure credentials are not configured.
     */
    private void initializeBlobStorage() {
        if (blobServiceClient == null && blobEndpoint != null && !blobEndpoint.isEmpty()) {
            blobServiceClient = new BlobServiceClientBuilder()
                    .endpoint(blobEndpoint)
                    .credential(new DefaultAzureCredentialBuilder().build())
                    .buildClient();
            containerClient = blobServiceClient.getBlobContainerClient(containerName);
            
            // Create container if it doesn't exist
            if (!containerClient.exists()) {
                containerClient.create();
            }
        }
    }

    /**
     * Generates monthly report and stores it in Azure Blob Storage.
     * 
     * @param month The month for the report
     * @param year The year for the report
     * @return Map containing report generation status and blob URL
     */
    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        Map<String, Object> result = new HashMap<>();

        try {
            initializeBlobStorage();

            // Generate report content
            StringBuilder reportContent = new StringBuilder();
            reportContent.append("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
            reportContent.append("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
            reportContent.append("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");

            // Upload to Azure Blob Storage
            BlobClient blobClient = containerClient.getBlobClient(fileName);
            byte[] reportBytes = reportContent.toString().getBytes(StandardCharsets.UTF_8);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(reportBytes);
            
            blobClient.upload(inputStream, reportBytes.length, true);

            result.put("status", "generated");
            result.put("blobUrl", blobClient.getBlobUrl());
            result.put("fileName", fileName);
            result.put("storageType", "Azure Blob Storage");
            result.put("serverPort", serverPort);

        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Failed to generate report: " + e.getMessage());
        }

        return result;
    }

    /**
     * Builds a cloud-native report download URL using externalized configuration.
     * 
     * @param reportName The name of the report file
     * @return HTTPS URL for report download
     */
    public String buildReportDownloadUrl(String reportName) {
        // Use externalized URL from Azure App Configuration with HTTPS
        String baseUrl = reportDownloadBaseUrl;
        if (!baseUrl.startsWith("https://")) {
            baseUrl = baseUrl.replace("http://", "https://");
        }
        return baseUrl + "/download/" + reportName;
    }

    /**
     * Retrieves system information with cloud-native configuration values.
     * 
     * @return Map containing system configuration information
     */
    public Map<String, Object> getSystemInfo() {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        Map<String, Object> info = new HashMap<>();
        info.put("storageType", "Azure Blob Storage");
        info.put("blobEndpoint", blobEndpoint);
        info.put("containerName", containerName);
        info.put("serverPort", serverPort);
        info.put("generatedAt", timestamp);
        return info;
    }

    /**
     * Schedules a report generation task using Azure Service Bus scheduled messages.
     * Replaces java.util.Timer with distributed, timezone-agnostic scheduling.
     * 
     * @param reportName The name of the report to generate
     * @param delaySeconds Delay in seconds before the task should execute
     * @return Status message
     */
    public String scheduleReportGeneration(String reportName, long delaySeconds) {
        try {
            if (serviceBusConnectionString == null || serviceBusConnectionString.isEmpty()) {
                return "Azure Service Bus not configured - scheduled task not created";
            }

            ServiceBusSenderClient senderClient = new ServiceBusClientBuilder()
                    .connectionString(serviceBusConnectionString)
                    .sender()
                    .queueName(serviceBusQueueName)
                    .buildClient();

            ServiceBusMessage message = new ServiceBusMessage("GENERATE_REPORT:" + reportName);
            message.setScheduledEnqueueTime(java.time.OffsetDateTime.now().plusSeconds(delaySeconds));

            senderClient.sendMessage(message);
            senderClient.close();

            return "Report generation scheduled via Azure Service Bus for " + reportName;
        } catch (Exception e) {
            return "Failed to schedule report: " + e.getMessage();
        }
    }
}
