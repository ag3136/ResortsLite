package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.cloud.storage.StorageException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

@Service
public class ReportService {

    private static final Logger logger = Logger.getLogger(ReportService.class.getName());

    // FIXED cr-java-0062: Replaced local file system writes with Google Cloud Storage
    // Using environment variables for cloud-native configuration
    private final String gcsBucketName;
    private final String gcsReportPrefix;
    private final String gcsBackupPrefix;
    private final Storage storage;

    // FIXED cr-java-0071: Externalized report download base URL using environment variable
    // This allows different URLs for dev, staging, and production without code changes
    @Value("${app.report.download.baseurl:https://reports.resorts-internal.com:8080}")
    private String reportDownloadBaseUrl;

    // VIOLATION [Software Portability / High]: Fixed server port hardcoded in application logic.
    // Container orchestration (ECS / EKS) dynamically assigns ports. Hardcoded ports prevent
    // dynamic port binding required for modern container deployment and service discovery.
    private static final int SERVER_PORT = 8080; // czr-port-001

    public ReportService() {
        // Initialize GCS client for cloud-native file storage
        this.storage = StorageOptions.getDefaultInstance().getService();
        
        // Read bucket configuration from environment variables with defaults
        // FIXED cr-java-0062: Using GCS bucket instead of local filesystem
        this.gcsBucketName = System.getenv().getOrDefault("GCS_BUCKET_NAME", "resort-reports-bucket");
        this.gcsReportPrefix = System.getenv().getOrDefault("GCS_REPORT_PREFIX", "reports/");
        this.gcsBackupPrefix = System.getenv().getOrDefault("GCS_BACKUP_PREFIX", "backups/nightly/");
        
        logger.info("ReportService initialized with GCS bucket: " + gcsBucketName);
    }

    /**
     * Generates a monthly report and stores it in Google Cloud Storage.
     * FIXED cr-java-0062: Replaced local file system write with GCS upload.
     * 
     * @param month The month for the report
     * @param year The year for the report
     * @return Map containing report generation status and GCS location
     */
    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        String gcsObjectPath = gcsReportPrefix + fileName; // FIXED cr-java-0062: Using GCS path

        Map<String, Object> result = new HashMap<>();

        try {
            // FIXED cr-java-0062: Create CSV content in memory instead of writing to local disk
            // This ensures data persistence across container restarts and scaling events
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
            
            // Generate CSV content
            writer.write("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
            writer.write("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
            writer.write("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");
            writer.flush();
            
            byte[] content = outputStream.toByteArray();
            writer.close();

            // FIXED cr-java-0062: Upload to Google Cloud Storage for durable persistence
            // Replaces ephemeral local filesystem writes that would be lost on container restart
            BlobId blobId = BlobId.of(gcsBucketName, gcsObjectPath);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType("text/csv")
                    .build();
            storage.create(blobInfo, content);

            logger.info("Report successfully uploaded to GCS: gs://" + gcsBucketName + "/" + gcsObjectPath);

            result.put("status", "generated");
            result.put("gcsPath", "gs://" + gcsBucketName + "/" + gcsObjectPath);
            result.put("bucket", gcsBucketName);
            result.put("objectPath", gcsObjectPath);
            result.put("serverPort", SERVER_PORT); // czr-port-001

        } catch (IOException e) {
            logger.severe("Failed to generate report content: " + e.getMessage());
            result.put("status", "error");
            result.put("message", "Failed to generate report: " + e.getMessage());
        } catch (StorageException e) {
            logger.severe("Failed to upload report to GCS: " + e.getMessage());
            result.put("status", "error");
            result.put("message", "Failed to upload to GCS: " + e.getMessage());
        }

        return result;
    }

    // VIOLATION [Code Sustainability / Medium]: No JavaDoc or method documentation.
    // Missing documentation is flagged across all public methods in the codebase.
    // This increases onboarding time and transformation risk for automated tools.
    public String buildReportDownloadUrl(String reportName) { // doc-missing-001
        // FIXED cr-java-0071: Using externalized configuration from environment variable
        // URL is now configurable via app.report.download.baseurl property or environment variable
        return reportDownloadBaseUrl + "/download/" + reportName;
    }

    /**
     * Returns system configuration information including GCS settings.
     * FIXED cr-java-0062: Returns GCS configuration instead of local file paths.
     * FIXED cr-java-0111: Replaced server-local time with UTC timestamp for cloud-native time handling.
     * 
     * @return Map containing system configuration
     */
    public Map<String, Object> getSystemInfo() { // doc-missing-001
        // FIXED cr-java-0111: Standardized on UTC timestamps to eliminate timezone inconsistencies
        // in distributed cloud environments. Using java.time.Instant for cloud-native time handling
        // across multiple regions and containers. Timezone context is explicitly indicated as UTC.
        String timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneOffset.UTC)
                .format(Instant.now()) + " UTC";
        
        Map<String, Object> info = new HashMap<>();
        // FIXED cr-java-0062: Returning GCS configuration instead of local file paths
        info.put("gcsBucket", gcsBucketName);
        info.put("gcsReportPrefix", gcsReportPrefix);
        info.put("gcsBackupPrefix", gcsBackupPrefix);
        info.put("serverPort", SERVER_PORT);        // czr-port-001
        info.put("generatedAt", timestamp);
        return info;
    }
}
