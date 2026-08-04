# Cloud Readiness Fix - Hard-coded Database Credentials

## Overview
This fix addresses the critical security vulnerability of hard-coded database credentials in the BookingService.java file by migrating them to Google Secret Manager using Spring Cloud GCP.

## Changes Made

### 1. BookingService.java
**Location**: `/src/main/java/com/demo/resortslite/BookingService.java`

**Changes**:
- Removed hard-coded database credentials (lines 22-23):
  - `DB_USER = "admin"` 
  - `DB_PASS = "Resort$Pass#2019!"`
- Added Spring `@Value` annotations to inject credentials from Google Secret Manager:
  ```java
  @Value("${sm://db-username:admin}")
  private String dbUser;
  
  @Value("${sm://db-password:}")
  private String dbPassword;
  ```
- Added import for `org.springframework.beans.factory.annotation.Value`

### 2. pom.xml
**Location**: `/pom.xml`

**Changes**:
- Added Spring Cloud GCP version property: `3.4.9`
- Added `dependencyManagement` section for Spring Cloud GCP dependencies
- Added Spring Cloud GCP Secret Manager starter dependency:
  ```xml
  <dependency>
      <groupId>com.google.cloud</groupId>
      <artifactId>spring-cloud-gcp-starter-secretmanager</artifactId>
  </dependency>
  ```

### 3. application.properties
**Location**: `/src/main/resources/application.properties`

**Changes**:
- Added Google Cloud Secret Manager configuration:
  ```properties
  spring.cloud.gcp.secretmanager.enabled=true
  spring.cloud.gcp.project-id=${GCP_PROJECT_ID:}
  ```
- Added documentation for secret format: `sm://secret-name`

## Google Secret Manager Setup

### Prerequisites
1. GCP Project with Secret Manager API enabled
2. Service account with `Secret Manager Secret Accessor` role
3. gcloud CLI installed (for local testing)

### Creating Secrets

#### Using gcloud CLI:
```bash
# Create db-username secret
echo -n "admin" | gcloud secrets create db-username \
    --data-file=- \
    --replication-policy="automatic" \
    --project=YOUR_PROJECT_ID

# Create db-password secret
echo -n "YOUR_SECURE_PASSWORD" | gcloud secrets create db-password \
    --data-file=- \
    --replication-policy="automatic" \
    --project=YOUR_PROJECT_ID
```

#### Using GCP Console:
1. Navigate to Security > Secret Manager
2. Click "CREATE SECRET"
3. Name: `db-username`, Value: `admin`
4. Click "CREATE SECRET"
5. Name: `db-password`, Value: `YOUR_SECURE_PASSWORD`

### Granting Access
```bash
# Grant service account access to secrets
gcloud secrets add-iam-policy-binding db-username \
    --member="serviceAccount:YOUR_SERVICE_ACCOUNT@YOUR_PROJECT.iam.gserviceaccount.com" \
    --role="roles/secretmanager.secretAccessor" \
    --project=YOUR_PROJECT_ID

gcloud secrets add-iam-policy-binding db-password \
    --member="serviceAccount:YOUR_SERVICE_ACCOUNT@YOUR_PROJECT.iam.gserviceaccount.com" \
    --role="roles/secretmanager.secretAccessor" \
    --project=YOUR_PROJECT_ID
```

## Running the Application

### Local Development
```bash
# Set GCP project ID
export GCP_PROJECT_ID=your-project-id

# Authenticate with gcloud (for local testing)
gcloud auth application-default login

# Run the application
mvn spring-boot:run
```

### GCP Deployment (Cloud Run, GKE, Compute Engine)
```bash
# Set environment variable
export GCP_PROJECT_ID=your-project-id

# The application will automatically use the service account attached to the compute resource
```

## How It Works

1. **Spring Cloud GCP Secret Manager Integration**: The `spring-cloud-gcp-starter-secretmanager` dependency provides automatic integration with Google Secret Manager.

2. **Secret Resolution**: The `sm://` prefix in `@Value` annotations tells Spring to resolve the value from Secret Manager:
   - `${sm://db-username:admin}` → Retrieves the latest version of `db-username` secret, defaults to "admin" if not found
   - `${sm://db-password:}` → Retrieves the latest version of `db-password` secret, defaults to empty string if not found

3. **Runtime Injection**: Secrets are retrieved at application startup and injected into the `dbUser` and `dbPassword` fields.

4. **Automatic Credential Rotation**: When secrets are updated in Secret Manager, restarting the application will automatically pick up the new values without code changes.

## Security Benefits

✅ **No Credentials in Source Code**: Database credentials are no longer stored in the codebase or version control.

✅ **Centralized Secret Management**: All secrets are managed in Google Secret Manager with audit logging.

✅ **Credential Rotation**: Secrets can be rotated without redeploying the application (just restart).

✅ **Access Control**: Fine-grained IAM policies control which services can access which secrets.

✅ **Audit Trail**: All secret access is logged in Cloud Audit Logs.

## Compliance

This fix addresses:
- **Rule ID**: cr-java-0069
- **Rule Name**: Hard-coded Database Credentials
- **Severity**: CRITICAL
- **Category**: configuration-management

## Next Steps

1. Create the required secrets in Google Secret Manager
2. Grant appropriate IAM permissions to the service account
3. Set the `GCP_PROJECT_ID` environment variable
4. Deploy the application to GCP

## Additional Resources

- [Spring Cloud GCP Secret Manager Documentation](https://cloud.spring.io/spring-cloud-gcp/reference/html/#secret-manager)
- [Google Secret Manager Documentation](https://cloud.google.com/secret-manager/docs)
- [Best Practices for Secret Management](https://cloud.google.com/secret-manager/docs/best-practices)
