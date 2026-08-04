package com.demo.resortslite;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    // FIXED cr-java-0061: Replaced hard-coded file paths with GCS bucket configuration
    // Using environment variables for cloud-native configuration
    @Value("${gcs.bucket.name}")
    private String gcsBucketName;

    @Value("${gcs.project.id:}")
    private String gcsProjectId;

    @Value("${gcs.reports.folder:reports/}")
    private String gcsReportsFolder;

    // FIXED cr-java-0071: Externalized report download base URL using environment variable
    // This enables seamless deployment across environments without code changes
    @Value("${report.download.base.url:https://reports.resorts-internal.com}")
    private String reportDownloadBaseUrl;

    // VIOLATION [Software Portability / High]: Fixed server port hardcoded in application logic.
    // Container orchestration (ECS / EKS) dynamically assigns ports. Hardcoded ports prevent
    // dynamic port binding required for modern container deployment and service discovery.
    private static final int SERVER_PORT = 8080; // czr-port-001

    /**
     * Initializes Google Cloud Storage client
     * @return Storage client instance
     */
    private Storage getStorageClient() {
        if (gcsProjectId != null && !gcsProjectId.isEmpty()) {
            return StorageOptions.newBuilder()
                    .setProjectId(gcsProjectId)
                    .build()
                    .getService();
        } else {
            // Use default credentials (Application Default Credentials)
            return StorageOptions.getDefaultInstance().getService();
        }
    }

    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        // FIXED cr-java-0061: Using GCS folder path instead of hard-coded file system path
        String gcsObjectPath = gcsReportsFolder + fileName;

        Map<String, Object> result = new HashMap<>();

        try {
            // FIXED cr-java-0061: Write report content to GCS instead of local file system
            Storage storage = getStorageClient();
            
            // Create CSV content in memory
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
            
            writer.write("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
            writer.write("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
            writer.write("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");
            writer.flush();
            writer.close();

            // Upload to GCS
            BlobId blobId = BlobId.of(gcsBucketName, gcsObjectPath);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType("text/csv")
                    .build();
            
            storage.create(blobInfo, outputStream.toByteArray());

            result.put("status", "generated");
            result.put("gcsPath", "gs://" + gcsBucketName + "/" + gcsObjectPath);
            result.put("bucket", gcsBucketName);
            result.put("objectPath", gcsObjectPath);
            result.put("serverPort", SERVER_PORT); // czr-port-001

        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "GCS operation failed: " + e.getMessage());
        }

        return result;
    }

    // VIOLATION [Code Sustainability / Medium]: No JavaDoc or method documentation.
    // Missing documentation is flagged across all public methods in the codebase.
    // This increases onboarding time and transformation risk for automated tools.
    public String buildReportDownloadUrl(String reportName) { // doc-missing-001
        // FIXED cr-java-0071: Using externalized report download base URL from environment variable
        // Default value uses HTTPS for cloud security compliance
        // Port is no longer hardcoded, allowing cloud load balancer to handle routing
        return reportDownloadBaseUrl + "/download/" + reportName;
    }

    public Map<String, Object> getSystemInfo() { // doc-missing-001
        // FIXED cr-java-0111: Replaced local time dependencies with UTC timestamps
        // Using java.time API with explicit UTC timezone for cloud-native distributed environments
        String timestamp = ZonedDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " UTC";
        Map<String, Object> info = new HashMap<>();
        // FIXED cr-java-0061: Return GCS configuration instead of hard-coded file paths
        info.put("gcsBucket", gcsBucketName);
        info.put("gcsReportsFolder", gcsReportsFolder);
        info.put("gcsProjectId", gcsProjectId != null && !gcsProjectId.isEmpty() ? gcsProjectId : "default");
        info.put("serverPort", SERVER_PORT);        // czr-port-001
        info.put("generatedAt", timestamp);
        return info;
    }
}
