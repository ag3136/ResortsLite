# Cloud Readiness Fix: Hard-coded Database Credentials (cr-java-0069)

## Summary
Fixed hard-coded database credentials in BookingService.java by migrating to Google Secret Manager with Spring Cloud GCP integration.

## Changes Made

### 1. BookingService.java
**Location**: `/src/main/java/com/demo/resortslite/BookingService.java`

**Before (Lines 22-23)**:
```java
private static final String DB_USER = "admin";                         // sec-cred-001
private static final String DB_PASS = "Resort$Pass#2019!";             // sec-cred-001
```

**After**:
```java
@Value("${DB_HOST:db-prod.resorts-internal.com}")
private String dbHost;

@Value("${sm://db-username:sa}")
private String dbUser;

@Value("${sm://db-password:}")
private String dbPassword;
```

**Changes**:
- Removed hard-coded database credentials (DB_USER and DB_PASS constants)
- Added `@Value` annotations to inject credentials from Google Secret Manager at runtime
- Used `sm://` prefix to indicate Spring Cloud GCP Secret Manager integration
- Added default values for local development (`:sa` and `:` for empty password)
- Added import for `org.springframework.beans.factory.annotation.Value`

### 2. pom.xml
**Added Dependencies**:
```xml
<properties>
    <spring-cloud-gcp.version>3.4.9</spring-cloud-gcp.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.google.cloud</groupId>
            <artifactId>spring-cloud-gcp-dependencies</artifactId>
            <version>${spring-cloud-gcp.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- Spring Cloud GCP Secret Manager for secure credential management -->
    <dependency>
        <groupId>com.google.cloud</groupId>
        <artifactId>spring-cloud-gcp-starter-secretmanager</artifactId>
    </dependency>
</dependencies>
```

### 3. application.properties
**Added Configuration**:
```properties
# FIXED: Database credentials now retrieved from Google Secret Manager
spring.datasource.url=${DB_URL:jdbc:h2:mem:resortdb;DB_CLOSE_DELAY=-1}
spring.datasource.username=${sm://db-username}
spring.datasource.password=${sm://db-password}

# Google Cloud Secret Manager Configuration
spring.cloud.gcp.secretmanager.enabled=true
spring.cloud.gcp.project-id=${GCP_PROJECT_ID:}
```

## Google Secret Manager Setup

### Prerequisites
1. GCP Project with Secret Manager API enabled
2. Service account with `roles/secretmanager.secretAccessor` permission
3. Application Default Credentials (ADC) configured

### Creating Secrets in GCP

#### Using gcloud CLI:
```bash
# Set your GCP project
export GCP_PROJECT_ID="your-project-id"

# Create secrets
echo -n "your-db-username" | gcloud secrets create db-username \
    --data-file=- \
    --project=${GCP_PROJECT_ID}

echo -n "your-db-password" | gcloud secrets create db-password \
    --data-file=- \
    --project=${GCP_PROJECT_ID}

echo -n "db-prod.resorts-internal.com" | gcloud secrets create db-host \
    --data-file=- \
    --project=${GCP_PROJECT_ID}

# Grant access to service account (replace with your service account)
gcloud secrets add-iam-policy-binding db-username \
    --member="serviceAccount:your-service-account@${GCP_PROJECT_ID}.iam.gserviceaccount.com" \
    --role="roles/secretmanager.secretAccessor" \
    --project=${GCP_PROJECT_ID}

gcloud secrets add-iam-policy-binding db-password \
    --member="serviceAccount:your-service-account@${GCP_PROJECT_ID}.iam.gserviceaccount.com" \
    --role="roles/secretmanager.secretAccessor" \
    --project=${GCP_PROJECT_ID}

gcloud secrets add-iam-policy-binding db-host \
    --member="serviceAccount:your-service-account@${GCP_PROJECT_ID}.iam.gserviceaccount.com" \
    --role="roles/secretmanager.secretAccessor" \
    --project=${GCP_PROJECT_ID}
```

#### Using GCP Console:
1. Navigate to **Security > Secret Manager**
2. Click **CREATE SECRET**
3. Create the following secrets:
   - Name: `db-username`, Value: your database username
   - Name: `db-password`, Value: your database password
   - Name: `db-host`, Value: your database host
4. Grant access to your service account with role `Secret Manager Secret Accessor`

## Deployment Configuration

### Environment Variables
Set the following environment variable when deploying:
```bash
export GCP_PROJECT_ID="your-gcp-project-id"
```

### For Cloud Run:
```bash
gcloud run deploy resortslite \
    --image gcr.io/${GCP_PROJECT_ID}/resortslite:latest \
    --set-env-vars GCP_PROJECT_ID=${GCP_PROJECT_ID} \
    --service-account your-service-account@${GCP_PROJECT_ID}.iam.gserviceaccount.com
```

### For GKE:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: resortslite
spec:
  template:
    spec:
      serviceAccountName: your-service-account
      containers:
      - name: resortslite
        image: gcr.io/your-project/resortslite:latest
        env:
        - name: GCP_PROJECT_ID
          value: "your-gcp-project-id"
```

## Local Development

For local development, the application will use default values:
- Username: `sa`
- Password: (empty)
- Database URL: `jdbc:h2:mem:resortdb;DB_CLOSE_DELAY=-1`

To test with actual GCP Secret Manager locally:
1. Install and configure gcloud CLI
2. Authenticate: `gcloud auth application-default login`
3. Set environment variable: `export GCP_PROJECT_ID="your-project-id"`
4. Run the application

## Security Benefits

1. **No Credentials in Source Code**: Credentials are no longer stored in version control
2. **Automated Rotation**: Credentials can be rotated in Secret Manager without code changes
3. **Audit Trail**: Secret Manager provides access logs for compliance
4. **Encryption**: Secrets are encrypted at rest and in transit
5. **Fine-grained Access Control**: IAM policies control who can access secrets
6. **Version Management**: Secret Manager maintains version history

## Testing

### Verify Secret Manager Integration:
```bash
# Check if secrets are accessible
gcloud secrets versions access latest --secret="db-username" --project=${GCP_PROJECT_ID}
gcloud secrets versions access latest --secret="db-password" --project=${GCP_PROJECT_ID}
```

### Application Startup:
The application will log Secret Manager initialization:
```
INFO: Fetching secret from Google Secret Manager: db-username
INFO: Fetching secret from Google Secret Manager: db-password
```

## Troubleshooting

### Common Issues:

1. **"Permission denied" errors**:
   - Ensure service account has `roles/secretmanager.secretAccessor` role
   - Verify GCP_PROJECT_ID is set correctly

2. **"Secret not found" errors**:
   - Verify secrets exist in Secret Manager
   - Check secret names match exactly (case-sensitive)

3. **"Application Default Credentials not found"**:
   - Run `gcloud auth application-default login` for local development
   - Ensure service account is configured for cloud deployments

## Compliance

This fix addresses:
- **OWASP A02:2021** - Cryptographic Failures (credentials exposure)
- **CIS Benchmark** - Secrets Management
- **PCI DSS 3.2.1** - Requirement 8.2.1 (credential storage)
- **NIST 800-53** - IA-5 (Authenticator Management)

## References

- [Spring Cloud GCP Secret Manager Documentation](https://cloud.spring.io/spring-cloud-gcp/reference/html/#secret-manager)
- [Google Cloud Secret Manager Best Practices](https://cloud.google.com/secret-manager/docs/best-practices)
- [Spring Boot Externalized Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config)
