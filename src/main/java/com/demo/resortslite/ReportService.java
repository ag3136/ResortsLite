package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    // blocker-2 (cz-java-0057): Replaced hardcoded absolute path "/var/legacy/reports/"
    // with an Amazon S3 bucket name sourced from the S3_REPORT_BUCKET environment variable,
    // eliminating filesystem dependency and enabling cross-platform container portability.
    @Value("${S3_REPORT_BUCKET:resorts-reports-bucket}")
    private String s3ReportBucket;

    // blocker-3 (cz-java-0057): Replaced hardcoded Windows-style absolute path
    // "C:\\ResortBackups\\nightly\\" with an Amazon S3 bucket name sourced from the
    // S3_BACKUP_BUCKET environment variable, removing OS-specific path dependency.
    @Value("${S3_BACKUP_BUCKET:resorts-backup-bucket}")
    private String s3BackupBucket;

    // blocker-11 (cz-java-0061): Replaced hardcoded port 8080 with an externalized
    // configuration value sourced from the SERVER_PORT environment variable (defaulting
    // to 8080), enabling dynamic port binding in ECS/EKS container deployments.
    @Value("${SERVER_PORT:8080}")
    private int serverPort;

    private final S3Client s3Client;

    public ReportService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        // blocker-2 (cz-java-0057): File is now written to Amazon S3 instead of the
        // local filesystem, using the S3_REPORT_BUCKET environment variable.
        String s3Key = "reports/" + fileName;

        Map<String, Object> result = new HashMap<>();

        try {
            String csvContent = "BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n"
                    + "BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n"
                    + "BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n";

            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(s3ReportBucket)
                    .key(s3Key)
                    .contentType("text/csv")
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromString(csvContent));

            result.put("status", "generated");
            result.put("path", "s3://" + s3ReportBucket + "/" + s3Key);
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
        // blocker-2 (cz-java-0057): reportPath now reflects S3 bucket reference
        info.put("reportBucket", s3ReportBucket);
        // blocker-3 (cz-java-0057): backupPath now reflects S3 backup bucket reference
        info.put("backupBucket", s3BackupBucket);
        // blocker-11 (cz-java-0061): serverPort now sourced from environment variable
        info.put("serverPort", serverPort);
        info.put("generatedAt", timestamp);
        return info;
    }
}
