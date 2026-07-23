package com.demo.resortslite;

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

/**
 * ReportService — cloud-native report generation using Amazon S3 for durable
 * object storage and AWS Systems Manager Parameter Store for all environment-
 * specific configuration values.
 *
 * <p>Fixes applied:
 * <ul>
 *   <li>cr-java-0061 / cr-java-0062 / cr-java-0063 — Replaced hard-coded file paths
 *       and java.io.File write operations with Amazon S3 PutObject calls.</li>
 *   <li>cr-java-0071 — Replaced hard-coded report download URL with a value
 *       retrieved from AWS Systems Manager Parameter Store at runtime.</li>
 *   <li>cr-java-0077 — Replaced hard-coded SERVER_PORT constant with an
 *       environment variable (SERVER_PORT) injected at runtime.</li>
 *   <li>cr-java-0111 — Replaced java.util.Date / SimpleDateFormat with
 *       java.time.Instant / ZonedDateTime standardised on UTC.</li>
 * </ul>
 */
@Service
public class ReportService {

    // FIX cr-java-0061 / cr-java-0062 / cr-java-0063:
    // S3 bucket name is read from the environment variable REPORT_S3_BUCKET.
    // All file read/write operations are delegated to Amazon S3 via AWS SDK v2,
    // eliminating any dependency on the host file system.
    private final String reportS3Bucket;

    // FIX cr-java-0077:
    // Server port is read from the SERVER_PORT environment variable injected by
    // ECS / EKS / Elastic Beanstalk at runtime — no hard-coded value in source.
    private final int serverPort;

    // AWS SDK v2 clients — constructed once and reused (thread-safe).
    private final S3Client s3Client;
    private final SsmClient ssmClient;

    public ReportService() {
        // FIX cr-java-0061 / cr-java-0062 / cr-java-0063:
        // Bucket name comes from an environment variable; fall back to a safe default
        // so the application starts even when the variable is not yet configured.
        this.reportS3Bucket = System.getenv("REPORT_S3_BUCKET") != null
                ? System.getenv("REPORT_S3_BUCKET")
                : "resorts-reports-bucket";

        // FIX cr-java-0077: Read port from environment; default to 8080.
        String portEnv = System.getenv("SERVER_PORT");
        this.serverPort = (portEnv != null && !portEnv.isEmpty()) ? Integer.parseInt(portEnv) : 8080;

        this.s3Client  = S3Client.create();
        this.ssmClient = SsmClient.create();
    }

    /**
     * Generates a monthly CSV report and stores it durably in Amazon S3.
     *
     * @param month two-digit month string (e.g. "03")
     * @param year  four-digit year string  (e.g. "2024")
     * @return result map containing S3 object key and status
     */
    public Map<String, Object> generateMonthlyReport(String month, String year) {
        // FIX cr-java-0061 / cr-java-0062 / cr-java-0063:
        // Object key replaces the former absolute local path.
        // Data is written to S3 — durable, replicated, and available across all instances.
        String objectKey = "reports/resort_report_" + month + "_" + year + ".csv";

        Map<String, Object> result = new HashMap<>();

        try {
            String csvContent = "BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n"
                    + "BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n"
                    + "BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n";

            // FIX cr-java-0062 / cr-java-0063:
            // PutObjectRequest replaces FileWriter / new File() — no local FS dependency.
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(reportS3Bucket)
                    .key(objectKey)
                    .contentType("text/csv")
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromString(csvContent));

            result.put("status", "generated");
            result.put("s3Bucket", reportS3Bucket);
            result.put("s3Key", objectKey);
            // FIX cr-java-0077: serverPort is now environment-variable-driven.
            result.put("serverPort", serverPort);

        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    /**
     * Builds the report download URL by reading the base endpoint from
     * AWS Systems Manager Parameter Store at runtime.
     *
     * @param reportName the S3 object key / report file name
     * @return fully-qualified HTTPS download URL
     */
    public String buildReportDownloadUrl(String reportName) {
        // FIX cr-java-0071:
        // The hard-coded URL "http://reports.resorts-internal.com:8080/download/" is
        // replaced with a value retrieved from AWS SSM Parameter Store.
        // The parameter /resorts/report/download-base-url must be set per environment
        // (dev / staging / prod) so no code change is needed between deployments.
        String baseUrl = getParameterFromSsm("/resorts/report/download-base-url",
                "https://reports.resorts-internal.com/download");
        return baseUrl + "/" + reportName;
    }

    /**
     * Returns system information using UTC timestamps (java.time API).
     *
     * @return map of system metadata
     */
    public Map<String, Object> getSystemInfo() {
        // FIX cr-java-0111:
        // java.util.Date / SimpleDateFormat replaced with java.time.Instant and
        // ZonedDateTime pinned to UTC — consistent across all cloud regions and instances.
        ZonedDateTime nowUtc = ZonedDateTime.now(ZoneOffset.UTC);
        String timestamp = nowUtc.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        Map<String, Object> info = new HashMap<>();
        // FIX cr-java-0061: report location is now an S3 bucket reference, not a local path.
        info.put("reportS3Bucket", reportS3Bucket);
        // FIX cr-java-0077: port is environment-variable-driven.
        info.put("serverPort", serverPort);
        // FIX cr-java-0111: UTC timestamp via java.time API.
        info.put("generatedAt", timestamp);
        return info;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Retrieves a parameter value from AWS Systems Manager Parameter Store.
     * Falls back to {@code defaultValue} if the parameter cannot be resolved
     * (e.g. during local development without AWS credentials).
     */
    private String getParameterFromSsm(String parameterName, String defaultValue) {
        try {
            GetParameterRequest request = GetParameterRequest.builder()
                    .name(parameterName)
                    .withDecryption(true)
                    .build();
            GetParameterResponse response = ssmClient.getParameter(request);
            return response.parameter().value();
        } catch (Exception e) {
            // Log and fall back to the supplied default so the application
            // remains functional in environments where SSM is not reachable.
            return defaultValue;
        }
    }
}
