package com.demo.resortslite;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    // FIXED cr-java-0061: Replaced hard-coded file paths with Azure Blob Storage configuration
    // Using environment variables for cloud-native configuration
    @Value("${azure.storage.connection-string}")
    private String azureStorageConnectionString;

    @Value("${azure.storage.container-name:resort-reports}")
    private String reportContainerName;

    @Value("${azure.storage.backup-container-name:resort-backups}")
    private String backupContainerName;

    @Value("${azure.storage.blob-endpoint:}")
    private String blobEndpoint;

    // FIXED cr-java-0111: Azure Service Bus configuration for distributed scheduling
    // Replaces local timer dependencies with cloud-native scheduled message delivery
    @Value("${azure.servicebus.connection-string:}")
    private String serviceBusConnectionString;

    @Value("${azure.servicebus.queue-name:report-scheduler}")
    private String serviceBusQueueName;

    // VIOLATION [Software Portability / High]: Fixed server port hardcoded in application logic.
    // Container orchestration (ECS / EKS) dynamically assigns ports. Hardcoded ports prevent
    // dynamic port binding required for modern container deployment and service discovery.
    private static final int SERVER_PORT = 8080; // czr-port-001

    // FIXED cr-java-0111: Timezone-agnostic date formatter using UTC
    // Replaces SimpleDateFormat which relies on server-local timezone
    private static final DateTimeFormatter UTC_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private BlobServiceClient blobServiceClient;
    private BlobContainerClient reportContainerClient;
    private BlobContainerClient backupContainerClient;
    
    // FIXED cr-java-0111: Azure Service Bus client for distributed scheduling
    private ServiceBusSenderClient serviceBusSenderClient;

    @PostConstruct
    public void init() {
        // Initialize Azure Blob Storage clients only if connection string is provided
        if (azureStorageConnectionString != null && !azureStorageConnectionString.isEmpty()) {
            try {
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
                System.err.println("Warning: Azure Blob Storage initialization failed. " +
                        "Ensure AZURE_STORAGE_CONNECTION_STRING is configured. Error: " + e.getMessage());
            }
        } else {
            System.err.println("Warning: Azure Blob Storage connection string not configured. " +
                    "Set AZURE_STORAGE_CONNECTION_STRING environment variable.");
        }

        // FIXED cr-java-0111: Initialize Azure Service Bus for distributed scheduling
        // Replaces java.util.Timer with cloud-native scheduled message delivery
        if (serviceBusConnectionString != null && !serviceBusConnectionString.isEmpty()) {
            try {
                serviceBusSenderClient = new ServiceBusClientBuilder()
                        .connectionString(serviceBusConnectionString)
                        .sender()
                        .queueName(serviceBusQueueName)
                        .buildClient();
                System.out.println("Azure Service Bus initialized for distributed scheduling");
            } catch (Exception e) {
                System.err.println("Warning: Azure Service Bus initialization failed. " +
                        "Ensure AZURE_SERVICEBUS_CONNECTION_STRING is configured. Error: " + e.getMessage());
            }
        } else {
            System.err.println("Warning: Azure Service Bus connection string not configured. " +
                    "Set AZURE_SERVICEBUS_CONNECTION_STRING environment variable for distributed scheduling.");
        }
    }

    @PreDestroy
    public void cleanup() {
        // Clean up Azure Service Bus resources
        if (serviceBusSenderClient != null) {
            try {
                serviceBusSenderClient.close();
            } catch (Exception e) {
                System.err.println("Error closing Service Bus sender: " + e.getMessage());
            }
        }
    }

    /**
     * FIXED cr-java-0111: Schedule a report generation task using Azure Service Bus
     * Replaces local timer-based scheduling with distributed, timezone-agnostic scheduling
     * 
     * @param month The month for the report
     * @param year The year for the report
     * @param delayMinutes Delay in minutes before the report should be generated
     * @return Status map indicating scheduling result
     */
    public Map<String, Object> scheduleReportGeneration(String month, String year, int delayMinutes) {
        Map<String, Object> result = new HashMap<>();
        
        if (serviceBusSenderClient == null) {
            result.put("status", "error");
            result.put("message", "Azure Service Bus not configured. Set AZURE_SERVICEBUS_CONNECTION_STRING.");
            return result;
        }

        try {
            // Create message payload with report parameters
            String messageBody = String.format("{\"month\":\"%s\",\"year\":\"%s\",\"type\":\"monthly_report\"}", 
                month, year);
            
            ServiceBusMessage message = new ServiceBusMessage(messageBody);
            
            // FIXED cr-java-0111: Use Azure Service Bus scheduled enqueue time for distributed scheduling
            // This is timezone-agnostic and works across distributed cloud environments
            Instant scheduledTime = Instant.now().plus(Duration.ofMinutes(delayMinutes));
            message.setScheduledEnqueueTime(scheduledTime.atOffset(ZoneOffset.UTC));
            
            // Send scheduled message
            serviceBusSenderClient.sendMessage(message);
            
            result.put("status", "scheduled");
            result.put("scheduledTime", UTC_FORMATTER.format(scheduledTime));
            result.put("month", month);
            result.put("year", year);
            result.put("queueName", serviceBusQueueName);
            
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Failed to schedule report: " + e.getMessage());
        }
        
        return result;
    }

    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";

        Map<String, Object> result = new HashMap<>();

        try {
            // FIXED cr-java-0061: Using Azure Blob Storage instead of local file system
            if (reportContainerClient == null) {
                result.put("status", "error");
                result.put("message", "Azure Blob Storage not configured. Set AZURE_STORAGE_CONNECTION_STRING.");
                return result;
            }

            // Create CSV content in memory
            StringBuilder csvContent = new StringBuilder();
            csvContent.append("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
            csvContent.append("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
            csvContent.append("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");

            // Upload to Azure Blob Storage
            BlobClient blobClient = reportContainerClient.getBlobClient(fileName);
            byte[] csvBytes = csvContent.toString().getBytes(StandardCharsets.UTF_8);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(csvBytes);
            blobClient.upload(inputStream, csvBytes.length, true);

            // FIXED cr-java-0061: Return Azure Blob Storage URL instead of local file path
            String blobUrl = blobClient.getBlobUrl();

            result.put("status", "generated");
            result.put("path", blobUrl);
            result.put("containerName", reportContainerName);
            result.put("blobName", fileName);
            result.put("serverPort", SERVER_PORT); // czr-port-001

        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    // VIOLATION [Code Sustainability / Medium]: No JavaDoc or method documentation.
    // Missing documentation is flagged across all public methods in the codebase.
    // This increases onboarding time and transformation risk for automated tools.
    public String buildReportDownloadUrl(String reportName) { // doc-missing-001
        // VIOLATION cr-java-0088 [Cloud Compatibility / Mandatory]: Plain HTTP URL
        // hardcoded for report download. Cloud security standards enforce HTTPS.
        return "http://reports.resorts-internal.com:8080/download/" + reportName; // cr-java-0088
    }

    public Map<String, Object> getSystemInfo() { // doc-missing-001
        // FIXED cr-java-0111: Use timezone-agnostic UTC timestamp instead of server-local time
        String timestamp = UTC_FORMATTER.format(Instant.now());
        Map<String, Object> info = new HashMap<>();
        
        // FIXED cr-java-0061: Return Azure Blob Storage configuration instead of hard-coded paths
        info.put("reportContainer", reportContainerName);
        info.put("backupContainer", backupContainerName);
        info.put("blobEndpoint", blobEndpoint != null && !blobEndpoint.isEmpty() ? blobEndpoint : "configured via connection string");
        info.put("storageConfigured", reportContainerClient != null);
        info.put("serviceBusConfigured", serviceBusSenderClient != null);
        info.put("serviceBusQueue", serviceBusQueueName);
        info.put("serverPort", SERVER_PORT);        // czr-port-001
        info.put("generatedAt", timestamp);
        info.put("timezone", "UTC");  // FIXED cr-java-0111: Explicitly indicate UTC timezone
        return info;
    }
}
