package com.demo.resortslite;

import com.azure.identity.DefaultAzureCredentialBuilder;
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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    // FIXED cr-java-0061, cr-java-0062, cr-java-0063: Replaced hardcoded file paths with Azure Blob Storage
    @Value("${azure.storage.account-name}")
    private String storageAccountName;

    @Value("${azure.storage.container-name:reports}")
    private String containerName;

    @Value("${azure.storage.connection-string}")
    private String storageConnectionString;

    // FIXED cr-java-0077: Replaced hardcoded port with environment variable
    @Value("${server.port:8080}")
    private int serverPort;

    // FIXED cr-java-0071: Externalized URL to Azure App Configuration
    @Value("${app.reports.base-url:https://reports.resorts-internal.com}")
    private String reportsBaseUrl;

    // FIXED cr-java-0111: Replaced java.util.Timer with Azure Service Bus scheduled messages
    @Value("${azure.servicebus.connection-string}")
    private String serviceBusConnectionString;

    @Value("${azure.servicebus.queue-name:scheduled-reports}")
    private String queueName;

    private BlobServiceClient blobServiceClient;
    private BlobContainerClient containerClient;
    private ServiceBusSenderClient serviceBusSenderClient;

    @PostConstruct
    public void init() {
        // FIXED cr-java-0061, cr-java-0062, cr-java-0063: Initialize Azure Blob Storage client
        blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(storageConnectionString)
                .buildClient();

        // Create container if it doesn't exist
        containerClient = blobServiceClient.getBlobContainerClient(containerName);
        if (!containerClient.exists()) {
            containerClient.create();
        }

        // FIXED cr-java-0111: Initialize Azure Service Bus client for scheduled messages
        serviceBusSenderClient = new ServiceBusClientBuilder()
                .connectionString(serviceBusConnectionString)
                .sender()
                .queueName(queueName)
                .buildClient();
    }

    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";

        Map<String, Object> result = new HashMap<>();

        try {
            // FIXED cr-java-0061, cr-java-0062, cr-java-0063: Write to Azure Blob Storage instead of local file system
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
            
            writer.write("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
            writer.write("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
            writer.write("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");
            writer.flush();
            writer.close();

            // Upload to Azure Blob Storage
            BlobClient blobClient = containerClient.getBlobClient(fileName);
            byte[] data = outputStream.toByteArray();
            ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
            blobClient.upload(inputStream, data.length, true);

            result.put("status", "generated");
            result.put("blobUrl", blobClient.getBlobUrl());
            result.put("fileName", fileName);
            result.put("container", containerName);
            result.put("serverPort", serverPort);

        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    /**
     * Builds a download URL for the specified report.
     * FIXED: Now uses HTTPS and externalized configuration.
     * 
     * @param reportName The name of the report file
     * @return The complete HTTPS URL for downloading the report
     */
    public String buildReportDownloadUrl(String reportName) {
        // FIXED cr-java-0071: Using externalized configuration from Azure App Configuration
        // FIXED cr-java-0088: Changed from HTTP to HTTPS for cloud security compliance
        return reportsBaseUrl + "/download/" + reportName;
    }

    /**
     * Retrieves system configuration information.
     * FIXED: Now returns cloud-native configuration values.
     * 
     * @return Map containing system configuration details
     */
    public Map<String, Object> getSystemInfo() {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        Map<String, Object> info = new HashMap<>();
        
        // FIXED cr-java-0061: Returning Azure Blob Storage configuration instead of local paths
        info.put("storageAccount", storageAccountName);
        info.put("containerName", containerName);
        info.put("serverPort", serverPort);
        info.put("reportsBaseUrl", reportsBaseUrl);
        info.put("generatedAt", timestamp);
        
        return info;
    }

    /**
     * Schedules a report generation task using Azure Service Bus.
     * FIXED cr-java-0111: Replaced java.util.Timer with Azure Service Bus scheduled messages.
     * 
     * @param reportType The type of report to generate
     * @param delayMinutes Delay in minutes before the report should be generated
     */
    public void scheduleReportGeneration(String reportType, int delayMinutes) {
        // FIXED cr-java-0111: Using Azure Service Bus for distributed, timezone-agnostic scheduling
        ServiceBusMessage message = new ServiceBusMessage("Generate report: " + reportType);
        message.setScheduledEnqueueTime(java.time.OffsetDateTime.now().plusMinutes(delayMinutes));
        
        serviceBusSenderClient.scheduleMessage(message, 
            java.time.OffsetDateTime.now().plusMinutes(delayMinutes));
    }
}
