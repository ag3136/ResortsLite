package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    // FIXED cr-java-0061: Replaced hard-coded file paths with S3 configuration
    // Using environment variables and Spring properties for cloud-native configuration
    @Value("${aws.s3.bucket.name}")
    private String s3BucketName;

    @Value("${aws.s3.region}")
    private String awsRegion;

    @Value("${aws.s3.reports.prefix}")
    private String reportsPrefix;

    @Value("${aws.s3.backups.prefix}")
    private String backupsPrefix;

    // FIXED cr-java-0077: Replaced hard-coded port with environment variable injection
    // Port is now dynamically configured via AWS Parameter Store or environment variables
    // This enables container orchestration (ECS/EKS) to assign ports dynamically
    @Value("${server.port:8080}")
    private int serverPort;

    private S3Client s3Client;

    // Initialize S3 client lazily to support AWS credentials from environment/IAM roles
    private S3Client getS3Client() {
        if (s3Client == null) {
            s3Client = S3Client.builder()
                    .region(Region.of(awsRegion))
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();
        }
        return s3Client;
    }

    /**
     * Generates a monthly report and stores it in Amazon S3.
     * FIXED cr-java-0061: Replaced local file system operations with S3 storage.
     *
     * @param month The month for the report
     * @param year The year for the report
     * @return Map containing the status and S3 object key
     */
    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        String s3Key = reportsPrefix + fileName; // FIXED cr-java-0061: Line 23 - Using S3 key instead of file path

        Map<String, Object> result = new HashMap<>();

        try {
            // Generate CSV content in memory
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
            
            writer.write("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
            writer.write("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
            writer.write("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");
            writer.flush();
            writer.close();

            byte[] reportContent = outputStream.toByteArray();

            // FIXED cr-java-0061: Line 37 & 42 - Upload to S3 instead of writing to local file system
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(s3BucketName)
                    .key(s3Key)
                    .contentType("text/csv")
                    .build();

            getS3Client().putObject(putObjectRequest, RequestBody.fromBytes(reportContent));

            result.put("status", "generated");
            result.put("s3Bucket", s3BucketName);
            result.put("s3Key", s3Key);
            result.put("s3Uri", "s3://" + s3BucketName + "/" + s3Key);
            result.put("serverPort", serverPort); // FIXED cr-java-0077: Using dynamic port configuration

        } catch (S3Exception e) {
            result.put("status", "error");
            result.put("message", "S3 error: " + e.awsErrorDetails().errorMessage());
        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", "IO error: " + e.getMessage());
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

    /**
     * Returns system information including S3 configuration.
     * FIXED cr-java-0061: Replaced hard-coded file paths with S3 bucket information.
     *
     * @return Map containing system configuration information
     */
    public Map<String, Object> getSystemInfo() { // doc-missing-001
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        Map<String, Object> info = new HashMap<>();
        
        // FIXED cr-java-0061: Replaced hard-coded paths with S3 configuration
        info.put("storageType", "AWS S3");
        info.put("s3Bucket", s3BucketName);
        info.put("s3Region", awsRegion);
        info.put("reportsPrefix", reportsPrefix);
        info.put("backupsPrefix", backupsPrefix);
        info.put("serverPort", serverPort); // FIXED cr-java-0077: Using dynamic port configuration
        info.put("generatedAt", timestamp);
        
        return info;
    }
}
