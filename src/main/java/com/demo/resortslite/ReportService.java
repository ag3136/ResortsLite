package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Value;
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

    // Fixed: cz-java-0057 - Externalized report base path to environment variable
    @Value("${app.report.path:#{systemEnvironment['REPORT_PATH'] ?: '/app/reports'}}")
    private String reportBasePath;

    // Fixed: cz-java-0057 - Externalized backup path to environment variable
    @Value("${app.backup.path:#{systemEnvironment['BACKUP_PATH'] ?: '/app/backups'}}")
    private String backupPath;

    // Fixed: cz-java-0061 - Externalized server port to environment variable for GKE ConfigMap compatibility
    // Container orchestration (GKE Autopilot) dynamically assigns ports. Hardcoded ports prevent
    // dynamic port binding required for modern container deployment and Cloud Load Balancing health checks.
    @Value("${server.port:#{systemEnvironment['SERVER_PORT'] ?: '8080'}}")
    private int serverPort; // Fixed: cz-java-0061

    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        String fullPath = reportBasePath + "/" + fileName; // Fixed: cz-java-0057

        Map<String, Object> result = new HashMap<>();

        try {
            File reportDir = new File(reportBasePath); // Fixed: cz-java-0057
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
            result.put("serverPort", serverPort); // Fixed: cz-java-0061

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
        return "http://reports.resorts-internal.com:" + serverPort + "/download/" + reportName; // Fixed: cz-java-0061, cr-java-0088
    }

    public Map<String, Object> getSystemInfo() { // doc-missing-001
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        Map<String, Object> info = new HashMap<>();
        info.put("reportPath", reportBasePath);  // Fixed: cz-java-0057
        info.put("backupPath", backupPath);      // Fixed: cz-java-0057
        info.put("serverPort", serverPort);     // Fixed: cz-java-0061
        info.put("generatedAt", timestamp);
        return info;
    }
}
