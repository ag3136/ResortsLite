package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    // S3 bucket name injected from environment variable (replaces hard-coded /var/legacy/reports/)
    @Value("${cloud.aws.s3.report-bucket:${REPORT_S3_BUCKET:resorts-lite-reports}}")
    private String reportBucket;

    // S3 key prefix injected from environment variable (replaces hard-coded backup path)
    @Value("${cloud.aws.s3.report-prefix:${REPORT_S3_PREFIX:reports/}}")
    private String reportPrefix;

    // Server port injected from environment variable (replaces hard-coded 8080)
    @Value("${server.port:${SERVER_PORT:8080}}")
    private int serverPort;

    private final S3Client s3Client;
    private final SsmClient ssmClient;

    public ReportService(S3Client s3Client, SsmClient ssmClient) {
        this.s3Client = s3Client;
        this.ssmClient = ssmClient;
    }

    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        // S3 object key replaces hard-coded absolute file path (cr-java-0061, cr-java-0062, cr-java-0063)
        String s3Key = reportPrefix + fileName;

        Map<String, Object> result = new HashMap<>();

        try {
            // Build CSV content in memory — no local file system dependency
            StringBuilder csvContent = new StringBuilder();
            csvContent.append("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
            csvContent.append("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
            csvContent.append("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");

            // Upload report directly to Amazon S3 (replaces FileWriter to local path)
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(reportBucket)
                    .key(s3Key)
                    .contentType("text/csv")
                    .build();
            s3Client.putObject(putRequest, RequestBody.fromString(csvContent.toString()));

            result.put("status", "generated");
            result.put("s3Bucket", reportBucket);
            result.put("s3Key", s3Key);
            result.put("serverPort", serverPort);

        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    public String buildReportDownloadUrl(String reportName) {
        // Retrieve report download base URL from AWS SSM Parameter Store (replaces hard-coded URL)
        // cr-java-0071: externalized environment URL via Parameter Store
        String paramName = System.getenv().getOrDefault(
                "REPORT_DOWNLOAD_URL_PARAM", "/resortslite/report/download-url");
        try {
            GetParameterResponse response = ssmClient.getParameter(
                    GetParameterRequest.builder().name(paramName).withDecryption(false).build());
            String baseUrl = response.parameter().value();
            return baseUrl + "/" + reportName;
        } catch (Exception e) {
            // Fallback: construct URL from environment variable
            String baseUrl = System.getenv().getOrDefault(
                    "REPORT_DOWNLOAD_BASE_URL", "https://reports.resorts-internal.com/download");
            return baseUrl + "/" + reportName;
        }
    }

    public Map<String, Object> getSystemInfo() {
        // cr-java-0111: Use java.time API with UTC (replaces java.util.Date / SimpleDateFormat)
        ZonedDateTime nowUtc = ZonedDateTime.now(ZoneOffset.UTC);
        String timestamp = nowUtc.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        Map<String, Object> info = new HashMap<>();
        info.put("reportBucket", reportBucket);   // S3 bucket replaces hard-coded file path
        info.put("reportPrefix", reportPrefix);   // S3 prefix replaces hard-coded backup path
        info.put("serverPort", serverPort);        // injected from env var
        info.put("generatedAt", timestamp);        // UTC timestamp via java.time
        return info;
    }
}
