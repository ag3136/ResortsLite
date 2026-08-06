package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
/**
 * Service for generating and managing resort reports using cloud-native storage.
 * FIXED cr-java-0062: Migrated from local file system writes to Amazon S3 for durable storage.
 * FIXED cr-java-0061: Replaced hard-coded file paths with S3 bucket configuration.
 * FIXED cr-java-0071: Replaced hard-coded environment URLs with AWS Parameter Store configuration.
 * FIXED cr-java-0077: Replaced hard-coded port with environment variable injection for cloud compatibility.
 */
@Service
public class ReportService {

    @Autowired
    private AwsParameterStoreConfig parameterStoreConfig;

    // FIXED cr-java-0061: Replaced hard-coded file paths with S3 bucket configuration
    // Using environment variables and Spring configuration for cloud-native storage
    @Value("${aws.s3.bucket.name}")
    private String s3BucketName;

    @Value("${aws.s3.region}")
    private String awsRegion;

    @Value("${aws.s3.reports.prefix}")
    private String reportsPrefix;

    @Value("${aws.s3.backups.prefix}")
    private String backupsPrefix;

    // FIXED cr-java-0077 [Networking & Communication / Critical]: Replaced hard-coded port with environment variable
    // injection from AWS Parameter Store. This enables dynamic port assignment required by container
    // orchestration platforms (ECS, EKS, Elastic Beanstalk) and prevents service conflicts.
    // Port is now configured via server.port property, which can be set by:
    // - Environment variable: SERVER_PORT
    // - ECS task definition environment variables
    // - EKS deployment manifests
    // - Elastic Beanstalk environment properties
    // - AWS Parameter Store (retrieved at runtime)
    // Default value of 8080 is used if not specified
    @Value("${server.port:8080}")
    private int serverPort;

    private S3Client s3Client;

    /**
     * Initialize S3 client with AWS credentials from environment or IAM role.
     * Uses DefaultCredentialsProvider which supports:
     * - Environment variables (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)
     * - IAM roles for EC2/ECS/EKS (recommended for production)
     * - AWS credentials file (~/.aws/credentials)
     * 
     * @return Configured S3Client instance
     */
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
     * Generate monthly report and store in Amazon S3.
     * FIXED cr-java-0062: Replaced local file system write operations (FileWriter) with S3 storage.
     * 
     * Benefits of S3 storage over local file system:
     * - Data persists across container restarts and scaling events
     * - Highly available and durable (99.999999999% durability)
     * - Accessible from multiple container instances
     * - Automatic backup and versioning capabilities
     * - No disk space limitations
     * 
     * @param month Report month
     * @param year Report year
     * @return Map containing report generation status and S3 location
     */
    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        String s3Key = reportsPrefix + fileName; // FIXED cr-java-0061: Using S3 key instead of file path

        Map<String, Object> result = new HashMap<>();

        try {
            // FIXED cr-java-0062: Generate report content in memory instead of local file system
            // Using ByteArrayOutputStream to avoid any local file I/O operations
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(outputStream);
            
            // Generate CSV report content
            writer.write("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
            writer.write("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
            writer.write("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");
            writer.flush();
            writer.close();

            // FIXED cr-java-0062: Upload to S3 instead of writing to local file system
            // This ensures data durability and availability in cloud/containerized environments
            byte[] reportContent = outputStream.toByteArray();
            
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(s3BucketName)
                    .key(s3Key)
                    .contentType("text/csv")
                    .build();

            getS3Client().putObject(putObjectRequest, RequestBody.fromBytes(reportContent));

            result.put("status", "generated");
            result.put("s3Bucket", s3BucketName); // FIXED cr-java-0061: Return S3 location instead of file path
            result.put("s3Key", s3Key); // FIXED cr-java-0061: Return S3 key instead of file path
            result.put("location", "s3://" + s3BucketName + "/" + s3Key);
            result.put("serverPort", serverPort); // FIXED cr-java-0077: Using environment variable

        } catch (S3Exception e) {
            result.put("status", "error");
            result.put("message", "S3 upload failed: " + e.awsErrorDetails().errorMessage());
        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", "Report generation failed: " + e.getMessage());
        }

        return result;
    }

    /**
     * Build report download URL.
     * FIXED cr-java-0071 [Cloud Compatibility / Mandatory]: Replaced hard-coded environment URL
     * with externalized configuration from AWS Systems Manager Parameter Store.
     * VIOLATION [Code Sustainability / Medium]: No JavaDoc or method documentation.
     * 
     * @param reportName Name of the report file
     * @return Download URL for the report
     */
    public String buildReportDownloadUrl(String reportName) { // doc-missing-001
        // FIXED cr-java-0071 [Cloud Compatibility / Mandatory]: Replaced hard-coded environment URL
        // with externalized configuration from AWS Systems Manager Parameter Store.
        // This enables environment-agnostic deployments without code changes.
        String baseUrl = parameterStoreConfig.getReportsServiceUrl(); // FIXED cr-java-0071
        return baseUrl + reportName;
    }

    /**
     * Get system information including S3 storage configuration.
     * FIXED cr-java-0061: Returns S3 bucket information instead of local file paths.
     * FIXED cr-java-0062: No longer references local file system paths.
     * FIXED cr-java-0077: Returns dynamically configured server port instead of hard-coded value.
     * 
        // FIXED cr-java-0111: Replaced java.util.Date/SimpleDateFormat with java.time API and standardized on UTC
        // Using Instant for current timestamp and DateTimeFormatter with UTC timezone to eliminate timezone inconsistencies
        String timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneOffset.UTC)
                .format(Instant.now());
        info.put("s3Bucket", s3BucketName);
        info.put("s3Region", awsRegion);
        info.put("reportsLocation", "s3://" + s3BucketName + "/" + reportsPrefix);
        info.put("backupsLocation", "s3://" + s3BucketName + "/" + backupsPrefix);
        info.put("serverPort", serverPort);        // FIXED cr-java-0077: Using environment variable
        info.put("generatedAt", timestamp);
        
        return info;
    }
}
