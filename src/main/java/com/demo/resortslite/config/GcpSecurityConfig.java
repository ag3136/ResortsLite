package com.demo.resortslite.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;

/**
 * FIXED cr-java-0090: GCP Security Configuration
 * 
 * This configuration class demonstrates proper integration with Google Cloud Platform
 * security services for cloud-native authentication and secrets management.
 * 
 * Key Features:
 * 1. Google Secret Manager integration for secure credential storage
 * 2. Cloud IAM for service-to-service authentication
 * 3. No file-based credential storage
 * 4. Automatic credential rotation support
 * 5. Workload Identity for GKE deployments
 * 
 * Migration from File-based Authentication:
 * - BEFORE: Credentials stored in local files (properties, config files)
 * - AFTER: Credentials stored in Google Secret Manager
 * - BEFORE: Manual credential rotation and distribution
 * - AFTER: Automatic rotation with Secret Manager versioning
 * - BEFORE: File permissions for security
 * - AFTER: Cloud IAM policies for access control
 * 
 * Setup Requirements:
 * 1. Create secrets in Google Secret Manager:
 *    gcloud secrets create db-username --data-file=username.txt
 *    gcloud secrets create db-password --data-file=password.txt
 * 
 * 2. Grant service account access to secrets:
 *    gcloud secrets add-iam-policy-binding db-username \
 *      --member="serviceAccount:SERVICE_ACCOUNT@PROJECT.iam.gserviceaccount.com" \
 *      --role="roles/secretmanager.secretAccessor"
 * 
 * 3. For GKE deployments, use Workload Identity:
 *    - Bind Kubernetes service account to GCP service account
 *    - No need to manage service account keys
 * 
 * 4. For local development, use Application Default Credentials:
 *    gcloud auth application-default login
 * 
 * Environment Variables:
 * - GCP_PROJECT_ID: Google Cloud project ID
 * - GOOGLE_APPLICATION_CREDENTIALS: Path to service account key (only for non-GKE)
 * 
 * Spring Cloud GCP automatically handles:
 * - Authentication using Cloud IAM
 * - Secret resolution from Secret Manager
 * - Credential caching and refresh
 */
@Configuration
public class GcpSecurityConfig {

    @Value("${spring.cloud.gcp.secretmanager.project-id:}")
    private String projectId;

    /**
     * Bean for accessing Google Secret Manager programmatically.
     * 
     * This is optional - Spring Cloud GCP automatically resolves ${sm://secret-name}
     * in application.properties. This bean is provided for cases where you need
     * to access secrets programmatically in your code.
     * 
     * Authentication is handled automatically via:
     * - Workload Identity (in GKE)
     * - Service Account (in GCE, Cloud Run, Cloud Functions)
     * - Application Default Credentials (local development)
     * 
     * @return SecretManagerServiceClient for programmatic secret access
     */
    @Bean
    public SecretManagerServiceClient secretManagerServiceClient() throws Exception {
        // Spring Cloud GCP automatically configures authentication
        // No need to manually load service account keys from files
        return SecretManagerServiceClient.create();
    }

    /**
     * Example method to demonstrate programmatic secret access.
     * 
     * In most cases, you should use Spring's @Value("${sm://secret-name}") instead.
     * This method is provided as an example for advanced use cases.
     * 
     * @param secretId The secret ID in Secret Manager
     * @param version The secret version (use "latest" for most recent)
     * @return The secret value as a String
     */
    public String accessSecretVersion(String secretId, String version) throws Exception {
        if (projectId == null || projectId.isEmpty()) {
            throw new IllegalStateException("GCP_PROJECT_ID must be set for Secret Manager access");
        }

        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            SecretVersionName secretVersionName = SecretVersionName.of(projectId, secretId, version);
            AccessSecretVersionResponse response = client.accessSecretVersion(secretVersionName);
            return response.getPayload().getData().toStringUtf8();
        }
    }

    /**
     * Configuration notes for Cloud IAM authentication:
     * 
     * 1. Service-to-Service Authentication:
     *    - Use service accounts with minimal required permissions
     *    - Enable Workload Identity for GKE deployments
     *    - Use Cloud IAM conditions for fine-grained access control
     * 
     * 2. Secret Manager IAM Roles:
     *    - roles/secretmanager.secretAccessor: Read secret values
     *    - roles/secretmanager.secretVersionManager: Manage versions
     *    - roles/secretmanager.admin: Full secret management
     * 
     * 3. Best Practices:
     *    - Never commit service account keys to source control
     *    - Use Workload Identity instead of service account keys
     *    - Rotate secrets regularly using Secret Manager versioning
     *    - Use separate secrets for different environments (dev/staging/prod)
     *    - Enable audit logging for secret access
     * 
     * 4. Migration Checklist:
     *    ✓ Move all credentials from files to Secret Manager
     *    ✓ Update application.properties to use ${sm://secret-name}
     *    ✓ Configure Cloud IAM permissions for service accounts
     *    ✓ Remove all credential files from source control
     *    ✓ Update deployment scripts to use Workload Identity
     *    ✓ Test credential rotation procedures
     *    ✓ Enable Secret Manager audit logging
     */
}
