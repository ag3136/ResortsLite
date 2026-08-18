package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    // FIXED cr-java-0063: Migrated from java.io.File to Amazon S3 for cloud-native storage
    // Using AWS SDK for Java v2 with environment-based configuration
    @Value("${aws.s3.bucket.name}")
    private String s3BucketName;

    @Value("${aws.s3.region}")
    private String awsRegion;

    @Value("${aws.s3.reports.prefix:reports/}")
    private String reportsPrefix;

    // FIXED cr-java-0077: Replaced hard-coded port with environment variable and Parameter Store support
    // Port can now be injected via SERVER_PORT environment variable or retrieved from Parameter Store
    // Falls back to server.port property (default 8080) for local development
    @Value("${SERVER_PORT:${server.port:8080}}")
    private int serverPort;

    // FIXED cr-java-0071: Externalized reports download URL using AWS Parameter Store
    // URL is retrieved from Parameter Store at runtime, enabling environment-agnostic deployments
    @Autowired
    @Lazy
    private com.demo.resortslite.config.ParameterStoreConfig parameterStoreConfig;

    private S3Client s3Client;

    @PostConstruct
    public void initializeS3Client() {
        // Initialize S3 client with default credentials provider
        // In AWS cloud environment, this will use IAM role credentials automatically
        s3Client = S3Client.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @PreDestroy
    public void closeS3Client() {
        if (s3Client != null) {
            s3Client.close();
        }
    }

    public Map<String, Object> generateMonthlyReport(String month, String year) {
        // FIXED cr-java-0063: Replaced hard-coded file path with S3 key prefix
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        String s3Key = reportsPrefix + fileName;

        Map<String, Object> result = new HashMap<>();

        try {
            // FIXED cr-java-0063 Lines 37-42: Replaced java.io.File operations with S3 upload
            // Original code used:
            //   Line 37: File reportDir = new File(REPORT_BASE_PATH);
            //   Line 39: reportDir.mkdirs();
            //   Line 42: FileWriter writer = new FileWriter(fullPath);
            // 
            // New approach: Generate report content in memory using ByteArrayOutputStream
            // and upload directly to S3 without local file system dependencies
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
            
            writer.write("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
            writer.write("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
            writer.write("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");
            writer.flush();
            writer.close();

            // Upload to S3 instead of writing to local file system
            byte[] reportContent = outputStream.toByteArray();
            
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(s3BucketName)
                    .key(s3Key)
                    .contentType("text/csv")
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(reportContent));

            result.put("status", "generated");
            result.put("s3Bucket", s3BucketName);
            result.put("s3Key", s3Key);
            result.put("s3Uri", "s3://" + s3BucketName + "/" + s3Key);
            result.put("serverPort", serverPort);

        } catch (S3Exception e) {
            result.put("status", "error");
            result.put("message", "S3 upload failed: " + e.awsErrorDetails().errorMessage());
        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", "Report generation failed: " + e.getMessage());
        }

        return result;
    }

    // VIOLATION [Code Sustainability / Medium]: No JavaDoc or method documentation.
    // Missing documentation is flagged across all public methods in the codebase.
    // This increases onboarding time and transformation risk for automated tools.
    public String buildReportDownloadUrl(String reportName) { // doc-missing-001
        // FIXED cr-java-0071: Hard-coded environment URL replaced with Parameter Store retrieval
        // Base URL is fetched from AWS Systems Manager Parameter Store at runtime
        // Falls back to HTTPS default if Parameter Store is unavailable
        String baseUrl = parameterStoreConfig.getParameterOrDefault(
                "/resortslite/reports-download-url",
                "https://reports.resorts-internal.com:8080"
        );
        return baseUrl + "/download/" + reportName;
    }

    public Map<String, Object> getSystemInfo() { // doc-missing-001
        // FIXED cr-java-0111: Replaced java.util.Date/SimpleDateFormat with java.time API
        // Using Instant with UTC timezone to ensure consistent timestamps across distributed cloud environments
        String timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneOffset.UTC)
                .format(Instant.now());
        Map<String, Object> info = new HashMap<>();
        // FIXED cr-java-0063: Replaced hard-coded file paths with S3 configuration
        info.put("s3BucketName", s3BucketName);
        info.put("s3Region", awsRegion);
        info.put("reportsPrefix", reportsPrefix);
        info.put("storageType", "AWS S3");
        info.put("serverPort", serverPort);
        info.put("generatedAt", timestamp);
        return info;
    }
}
