package com.demo.resortslite;

import com.azure.core.util.BinaryData;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    private final String reportContainerName;
    private final String backupContainerName;
    private final String reportDownloadBaseUrl;
    private final String configuredServerPort;
    private final String storageConnectionString;
    private final String storageEndpoint;
    private final String serviceBusConnectionString;
    private final String serviceBusQueueName;
    private final String serviceBusTopicName;
    private final boolean useTopic;
    private final Clock clock;

    public ReportService(
            @Value("${app.report.container-name}") String reportContainerName,
            @Value("${app.report.backup-container-name}") String backupContainerName,
            @Value("${app.report.download-base-url}") String reportDownloadBaseUrl,
            @Value("${app.report.server-port}") String configuredServerPort,
            @Value("${app.report.storage.connection-string:}") String storageConnectionString,
            @Value("${app.report.storage.endpoint:}") String storageEndpoint,
            @Value("${app.report.schedule.connection-string:}") String serviceBusConnectionString,
            @Value("${app.report.schedule.queue-name}") String serviceBusQueueName,
            @Value("${app.report.schedule.topic-name}") String serviceBusTopicName,
            @Value("${app.report.schedule.use-topic:false}") boolean useTopic) {
        this.reportContainerName = reportContainerName;
        this.backupContainerName = backupContainerName;
        this.reportDownloadBaseUrl = reportDownloadBaseUrl;
        this.configuredServerPort = configuredServerPort;
        this.storageConnectionString = storageConnectionString;
        this.storageEndpoint = storageEndpoint;
        this.serviceBusConnectionString = serviceBusConnectionString;
        this.serviceBusQueueName = serviceBusQueueName;
        this.serviceBusTopicName = serviceBusTopicName;
        this.useTopic = useTopic;
        this.clock = Clock.systemUTC();
    }

    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        String reportContent = buildReportContent();
        Map<String, Object> result = new HashMap<>();

        try {
            BlobContainerClient reportContainer = getBlobContainerClient(reportContainerName);
            reportContainer.getBlobClient(fileName)
                    .upload(BinaryData.fromString(reportContent), true);

            result.put("status", "generated");
            result.put("path", reportContainer.getBlobClient(fileName).getBlobUrl());
            result.put("serverPort", configuredServerPort);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    public String buildReportDownloadUrl(String reportName) {
        return reportDownloadBaseUrl + "/" + reportName;
    }

    public Map<String, Object> getSystemInfo() {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date.from(clock.instant()));
        Map<String, Object> info = new HashMap<>();
        info.put("reportContainer", reportContainerName);
        info.put("backupContainer", backupContainerName);
        info.put("serverPort", configuredServerPort);
        info.put("generatedAt", timestamp);
        return info;
    }

    public Map<String, Object> scheduleReportGeneration(String reportName, Duration delay) {
        Map<String, Object> response = new HashMap<>();
        if (!StringUtils.hasText(serviceBusConnectionString)) {
            response.put("status", "skipped");
            response.put("message", "Azure Service Bus connection string is not configured");
            return response;
        }

        OffsetDateTime scheduledTime = OffsetDateTime.now(clock).plus(delay).withOffsetSameInstant(ZoneOffset.UTC);
        ServiceBusMessage message = new ServiceBusMessage(reportName.getBytes(StandardCharsets.UTF_8));
        message.setScheduledEnqueueTime(scheduledTime);

        try {
            if (useTopic) {
                new ServiceBusClientBuilder()
                        .connectionString(serviceBusConnectionString)
                        .sender()
                        .topicName(serviceBusTopicName)
                        .buildClient()
                        .scheduleMessage(message, scheduledTime);
            } else {
                new ServiceBusClientBuilder()
                        .connectionString(serviceBusConnectionString)
                        .sender()
                        .queueName(serviceBusQueueName)
                        .buildClient()
                        .scheduleMessage(message, scheduledTime);
            }
            response.put("status", "scheduled");
            response.put("scheduledFor", scheduledTime.toString());
        } catch (Exception ex) {
            response.put("status", "error");
            response.put("message", ex.getMessage());
        }
        return response;
    }

    private String buildReportContent() {
        return "BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n"
                + "BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n"
                + "BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n";
    }

    private BlobContainerClient getBlobContainerClient(String containerName) {
        BlobServiceClient blobServiceClient = buildBlobServiceClient();
        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
        if (!containerClient.exists()) {
            containerClient.create();
        }
        return containerClient;
    }

    private BlobServiceClient buildBlobServiceClient() {
        BlobServiceClientBuilder builder = new BlobServiceClientBuilder();
        if (StringUtils.hasText(storageConnectionString)) {
            builder.connectionString(storageConnectionString);
        } else if (StringUtils.hasText(storageEndpoint)) {
            builder.endpoint(storageEndpoint)
                    .credential(new DefaultAzureCredentialBuilder().build());
        } else {
            throw new IllegalStateException("Azure Blob Storage configuration is missing");
        }
        return builder.buildClient();
    }
}
