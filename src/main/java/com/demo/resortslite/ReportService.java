package com.demo.resortslite;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Cloud-ready report service that uses Azure Blob Storage for all file operations
 * instead of local file system paths, and Azure Service Bus for scheduled tasks
 * instead of java.util.Timer.
 */
@Service
public class ReportService {

    // Configuration externalized to environment variables / application.properties
    @Value("${app.report.base-path:/var/legacy/reports/}")
    private String reportBasePath;

    @Value("${app.backup.path:/var/legacy/backups/}")
    private String backupPath;

    @Value("${azure.storage.blob.container-name:resort-reports}")
    private String blobContainerName;

    @Value("${azure.storage.blob.connection-string:}")
    private String storageConnectionString;

    @Value("${app.report.download.url:https://reports.resorts-internal.com/download/}")
    private String reportDownloadBaseUrl;

    @Value("${server.port:8080}")
    private String serverPort;

    @Autowired(required = false)
    private ServiceBusSenderClient serviceBusSenderClient;

    /**
     * Lazily-initialized Azure Blob Container client using DefaultAzureCredential.
     * Credentials are resolved from the environment (Managed Identity, service principal, etc.).
     */
    private BlobContainerClient blobContainerClient;

    private BlobContainerClient getBlobContainerClient() {
        if (blobContainerClient == null) {
            if (storageConnectionString != null && !storageConnectionString.isEmpty()) {
                blobContainerClient = new BlobContainerClientBuilder()
                        .connectionString(storageConnectionString)
                        .containerName(blobContainerName)
                        .buildClient();
            } else {
                // Use DefaultAzureCredential for token-based authentication (Managed Identity)
                blobContainerClient = new BlobContainerClientBuilder()
                        .endpoint("https://" + blobContainerName + ".blob.core.windows.net")
                        .credential(new DefaultAzureCredentialBuilder().build())
                        .containerName(blobContainerName)
                        .buildClient();
            }
            // Ensure container exists
            blobContainerClient.createIfNotExists();
        }
        return blobContainerClient;
    }

    /**
     * Generates a monthly report and stores it in Azure Blob Storage.
     * Replaces all local file system write operations with Azure Blob Storage.
     *
     * @param month the month for the report
     * @param year  the year for the report
     * @return a map containing the report status and blob URL
     */
    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";

        Map<String, Object> result = new HashMap<>();

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n".getBytes(StandardCharsets.UTF_8));
            baos.write("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n".getBytes(StandardCharsets.UTF_8));
            baos.write("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n".getBytes(StandardCharsets.UTF_8));

            byte[] reportBytes = baos.toByteArray();

            // Upload to Azure Blob Storage instead of local file system
            BlobContainerClient containerClient = getBlobContainerClient();
            BlobClient blobClient = containerClient.getBlobClient(fileName);
            blobClient.upload(new ByteArrayInputStream(reportBytes), reportBytes.length, true);

            String blobUrl = blobClient.getBlobUrl();

            result.put("status", "generated");
            result.put("path", blobUrl);
            result.put("serverPort", serverPort);

        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Azure Blob Storage error: " + e.getMessage());
        }

        return result;
    }

    /**
     * Builds a report download URL using HTTPS and externalized configuration.
     *
     * @param reportName the name of the report
     * @return the HTTPS download URL
     */
    public String buildReportDownloadUrl(String reportName) {
        return reportDownloadBaseUrl + reportName;
    }

    /**
     * Returns system information with cloud-native configuration values.
     * Uses UTC timezone for consistent timestamps across distributed instances.
     *
     * @return a map of system info
     */
    public Map<String, Object> getSystemInfo() {
        String timestamp = DateTimeFormatter.ISO_INSTANT
                .withZone(ZoneOffset.UTC)
                .format(Instant.now());
        Map<String, Object> info = new HashMap<>();
        info.put("reportPath", reportBasePath);
        info.put("backupPath", backupPath);
        info.put("serverPort", serverPort);
        info.put("generatedAt", timestamp);
        return info;
    }

    /**
     * Schedules a report generation task using Azure Service Bus scheduled messages.
     * Replaces java.util.Timer and server-local scheduling with distributed,
     * timezone-agnostic task execution.
     *
     * @param month     the month for the report
     * @param year      the year for the report
     * @param delaySecs the delay in seconds before the message is delivered
     * @return a map containing the scheduling status
     */
    public Map<String, Object> scheduleReportGeneration(String month, String year, long delaySecs) {
        Map<String, Object> result = new HashMap<>();

        if (serviceBusSenderClient == null) {
            result.put("status", "error");
            result.put("message", "Azure Service Bus is not configured");
            return result;
        }

        try {
            String messageBody = "REPORT_SCHEDULE:" + month + ":" + year;
            Instant deliveryTime = Instant.now().plusSeconds(delaySecs);

            serviceBusSenderClient.sendMessage(
                    new com.azure.messaging.servicebus.ServiceBusMessage(messageBody)
                            .setScheduledEnqueueTime(deliveryTime)
            );

            result.put("status", "scheduled");
            result.put("scheduledFor", deliveryTime.toString());
            result.put("message", "Report generation scheduled via Azure Service Bus");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Failed to schedule report: " + e.getMessage());
        }

        return result;
    }
}
