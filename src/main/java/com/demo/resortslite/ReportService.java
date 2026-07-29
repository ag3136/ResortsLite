package com.demo.resortslite;

import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    // FIXED blocker-2 (cz-java-0057): Replaced absolute path with environment variable
    // Path should be injected via Kubernetes ConfigMap as environment variable
    private String getReportBasePath() {
        String path = System.getenv("REPORT_BASE_PATH");
        return (path != null && !path.isEmpty()) ? path : "/app/reports";
    }

    // FIXED blocker-3 (cz-java-0057): Replaced Windows absolute path with environment variable
    // Path should be injected via Kubernetes ConfigMap as environment variable
    private String getBackupPath() {
        String path = System.getenv("BACKUP_PATH");
        return (path != null && !path.isEmpty()) ? path : "/app/backups";
    }

    // FIXED blocker-11 (cz-java-0061): Replaced hardcoded port with environment variable
    // Port should be injected via Kubernetes ConfigMap as environment variable
    private int getServerPort() {
        String port = System.getenv("SERVER_PORT");
        return (port != null && !port.isEmpty()) ? Integer.parseInt(port) : 8080;
    }

    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        String reportBasePath = getReportBasePath();
        String fullPath = reportBasePath + "/" + fileName;

        Map<String, Object> result = new HashMap<>();

        try {
            File reportDir = new File(reportBasePath);
            if (!reportDir.exists()) {
                reportDir.mkdirs();
            }

            FileWriter writer = new FileWriter(fullPath);
            writer.write("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
            writer.write("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
            writer.write("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");
            writer.close();

            result.put("status", "generated");
            result.put("path", fullPath);
            result.put("serverPort", getServerPort());

        } catch (IOException e) {
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
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        Map<String, Object> info = new HashMap<>();
        info.put("reportPath", getReportBasePath());
        info.put("backupPath", getBackupPath());
        info.put("serverPort", getServerPort());
        info.put("generatedAt", timestamp);
        return info;
    }
}
