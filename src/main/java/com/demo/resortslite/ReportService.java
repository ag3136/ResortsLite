package com.demo.resortslite;

import com.azure.core.util.BinaryData;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    private static final String DEFAULT_BLOB_ENDPOINT = "https://localhost.blob.core.windows.net";
    private static final String DEFAULT_REPORT_CONTAINER = "reports";
    private static final String DEFAULT_BACKUP_CONTAINER = "report-backups";
    private static final String DEFAULT_DOWNLOAD_URL = "https://reports.resorts-internal.com/download/";
    private static final String DEFAULT_SERVER_PORT = "8080";
    private static final String DEFAULT_SERVICE_BUS_NAMESPACE = "localhost.servicebus.windows.net";
    private static final String DEFAULT_SERVICE_BUS_QUEUE = "report-jobs";

    private final BlobContainerClient reportContainerClient;
    private final BlobContainerClient backupContainerClient;

    public ReportService() {
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .endpoint(System.getenv().getOrDefault("AZURE_STORAGE_BLOB_ENDPOINT", DEFAULT_BLOB_ENDPOINT))
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
        this.reportContainerClient = buildContainerClient(blobServiceClient,
                System.getenv().getOrDefault("AZURE_REPORTS_CONTAINER", DEFAULT_REPORT_CONTAINER));
        this.backupContainerClient = buildContainerClient(blobServiceClient,
                System.getenv().getOrDefault("AZURE_REPORTS_BACKUP_CONTAINER", DEFAULT_BACKUP_CONTAINER));
    }

    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        String reportContent = "BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n"
                + "BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n"
                + "BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n";

        Map<String, Object> result = new HashMap<>();

        try {
            reportContainerClient.getBlobClient(fileName).upload(BinaryData.fromString(reportContent), true);
            backupContainerClient.getBlobClient(fileName).upload(BinaryData.fromString(reportContent), true);

            result.put("status", "generated");
            result.put("path", reportContainerClient.getBlobClient(fileName).getBlobUrl());
            result.put("serverPort", resolveServerPort());

        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    public String buildReportDownloadUrl(String reportName) {
        return System.getenv().getOrDefault("REPORT_DOWNLOAD_BASE_URL", DEFAULT_DOWNLOAD_URL) + reportName;
    }

    public Map<String, Object> getSystemInfo() {
        String timestamp = OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        Map<String, Object> info = new HashMap<>();
        info.put("reportPath", reportContainerClient.getBlobContainerUrl());
        info.put("backupPath", backupContainerClient.getBlobContainerUrl());
        info.put("serverPort", resolveServerPort());
        info.put("generatedAt", timestamp);
        return info;
    }

    public void scheduleReportGeneration(String month, String year) {
        String namespace = System.getenv().getOrDefault("AZURE_SERVICEBUS_NAMESPACE", DEFAULT_SERVICE_BUS_NAMESPACE);
        String queueName = System.getenv().getOrDefault("AZURE_SERVICEBUS_QUEUE", DEFAULT_SERVICE_BUS_QUEUE);
        String fullyQualifiedNamespace = namespace;
        OffsetDateTime scheduledTime = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);

        try (ServiceBusSenderClient senderClient = new ServiceBusClientBuilder()
                .credential(fullyQualifiedNamespace, new DefaultAzureCredentialBuilder().build())
                .sender()
                .queueName(queueName)
                .buildClient()) {
            ServiceBusMessage message = new ServiceBusMessage("generate-report:" + month + ":" + year)
                    .setScheduledEnqueueTime(scheduledTime);
            senderClient.scheduleMessage(message, scheduledTime);
        }
    }

    private BlobContainerClient buildContainerClient(BlobServiceClient blobServiceClient, String containerName) {
        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
        if (!containerClient.exists()) {
            containerClient.create();
        }
        return containerClient;
    }

    private String resolveServerPort() {
        return System.getenv().getOrDefault("SERVER_PORT", DEFAULT_SERVER_PORT);
    }
}
