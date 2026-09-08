package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    @Autowired
    private AzureBlobStorageService azureBlobStorageService;

    @Autowired
    private AzureAppConfigurationService azureAppConfigurationService;

    @Autowired
    private AzureServiceBusScheduler azureServiceBusScheduler;

    private final String configuredServerPort;

    public ReportService(@Value("${app.report.server-port:${SERVER_PORT:8080}}") String configuredServerPort) {
        this.configuredServerPort = configuredServerPort;
    }

    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        String reportContent = "BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n"
                + "BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n"
                + "BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n";

        Map<String, Object> result = new HashMap<>();

        try {
            String blobUrl = azureBlobStorageService.uploadText(fileName, reportContent);
            result.put("status", "generated");
            result.put("path", blobUrl);
            result.put("serverPort", resolveServerPort());
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    public String buildReportDownloadUrl(String reportName) {
        String baseUrl = azureAppConfigurationService.getValue(
                "app.reports.download-base-url",
                "https://reports.example.internal/download/");
        return baseUrl + reportName;
    }

    public Map<String, Object> getSystemInfo() {
        String timestamp = OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        Map<String, Object> info = new HashMap<>();
        info.put("reportStorage", "azure-blob-storage");
        info.put("backupStorage", "azure-blob-storage");
        info.put("serverPort", resolveServerPort());
        info.put("generatedAt", timestamp);
        return info;
    }

    public void scheduleReportGeneration(String month, String year) {
        azureServiceBusScheduler.scheduleReportGeneration(month, year, OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5));
    }

    private String resolveServerPort() {
        return azureAppConfigurationService.getValue("app.report.server-port", configuredServerPort);
    }
}
