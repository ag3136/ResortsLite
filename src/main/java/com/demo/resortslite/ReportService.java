package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    @Value("${aws.s3.bucket:resorts-reports-bucket}")
    private String bucketName;

    @Value("${reports.download.url}")
    private String reportsDownloadUrl;

    @Value("${server.port:8080}")
    private int serverPort;

    private final S3Client s3Client;

    public ReportService() {
        this.s3Client = S3Client.builder().build();
    }

    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        
        Map<String, Object> result = new HashMap<>();

        try {
            String content = "BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n" +
                             "BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n" +
                             "BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n";

            PutObjectRequest putOb = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .build();

            s3Client.putObject(putOb, RequestBody.fromString(content, StandardCharsets.UTF_8));

            result.put("status", "generated");
            result.put("path", "s3://" + bucketName + "/" + fileName);
            result.put("serverPort", serverPort);

        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    public String buildReportDownloadUrl(String reportName) {
        return reportsDownloadUrl + "/download/" + reportName;
    }

    public Map<String, Object> getSystemInfo() {
        String timestamp = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        Map<String, Object> info = new HashMap<>();
        info.put("reportPath", "s3://" + bucketName + "/");
        info.put("backupPath", "s3://" + bucketName + "/backups/");
        info.put("serverPort", serverPort);
        info.put("generatedAt", timestamp);
        return info;
    }
}
