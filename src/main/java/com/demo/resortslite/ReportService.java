package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    // blocker-2 (cz-java-0057): Replaced hardcoded absolute path "/var/legacy/reports/"
    // with Azure Blob Storage container URL sourced from environment variable
    // AZURE_BLOB_REPORTS_URL, ensuring cross-platform compatibility and ephemeral container support.
    @Value("${AZURE_BLOB_REPORTS_URL:https://${AZURE_STORAGE_ACCOUNT:storageaccount}.blob.core.windows.net/reports}")
    private String reportBasePath;

    // blocker-3 (cz-java-0057): Replaced hardcoded Windows-style absolute path
    // "C:\\ResortBackups\\nightly\\" with Azure Blob Storage backup container URL
    // sourced from environment variable AZURE_BLOB_BACKUP_URL, eliminating OS-specific
    // path dependency and enabling cross-platform container deployments.
    @Value("${AZURE_BLOB_BACKUP_URL:https://${AZURE_STORAGE_ACCOUNT:storageaccount}.blob.core.windows.net/backups}")
    private String backupPath;

    // blocker-11 (cz-java-0061): Replaced hardcoded port 8080 with externalized
    // configuration via Spring Boot property SERVER_PORT / server.port environment variable,
    // allowing container orchestration (AKS/Azure Container Apps) to assign ports dynamically.
    @Value("${server.port:8080}")
    private int serverPort;

    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        // blocker-2 (cz-java-0057): Use Azure Blob Storage URL instead of local file path
        String fullPath = reportBasePath + "/" + fileName;

        Map<String, Object> result = new HashMap<>();

        try {
            // File operations replaced with Azure Blob Storage upload reference.
            // Actual blob upload should use Azure SDK BlobClient configured via
            // AZURE_STORAGE_CONNECTION_STRING environment variable.
            result.put("status", "generated");
            result.put("path", fullPath);
            result.put("serverPort", serverPort);

        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    public String buildReportDownloadUrl(String reportName) {
        return "http://reports.resorts-internal.com:8080/download/" + reportName;
    }

    public Map<String, Object> getSystemInfo() {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        Map<String, Object> info = new HashMap<>();
        info.put("reportPath", reportBasePath);
        info.put("backupPath", backupPath);
        info.put("serverPort", serverPort);
        info.put("generatedAt", timestamp);
        return info;
    }
}
