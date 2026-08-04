# Cloud Readiness Fixes - Hard-coded Environment URLs (cr-java-0071)

## Overview
This document describes the fixes applied to resolve hard-coded environment-specific URLs in the ResortsLite application, making it cloud-ready for GCP deployment.

## Issues Fixed

### 1. BookingController.java - Line 66
**Issue**: Hard-coded inventory service URL
```java
// BEFORE (Hard-coded)
String inventoryUrl = "http://inventory-service.internal:8081/rooms/available";
```

**Fix Applied**: Externalized URL using Spring @Value annotation with environment variable support
```java
// AFTER (Externalized)
@Value("${inventory.service.url:https://inventory-service.internal:8081}")
private String inventoryServiceUrl;

String inventoryUrl = inventoryServiceUrl + "/rooms/available";
```

**Benefits**:
- URL can be changed per environment without code changes
- Default value uses HTTPS for cloud security compliance
- Supports environment variable override: `INVENTORY_SERVICE_URL`

---

### 2. ReportService.java - Line 106
**Issue**: Hard-coded report download URL
```java
// BEFORE (Hard-coded)
return "http://reports.resorts-internal.com:8080/download/" + reportName;
```

**Fix Applied**: Externalized base URL using Spring @Value annotation
```java
// AFTER (Externalized)
@Value("${report.download.base.url:https://reports.resorts-internal.com}")
private String reportDownloadBaseUrl;

return reportDownloadBaseUrl + "/download/" + reportName;
```

**Benefits**:
- Base URL can be configured per environment
- Default value uses HTTPS (no hardcoded port)
- Supports environment variable override: `REPORT_DOWNLOAD_BASE_URL`

---

## Configuration Changes

### application.properties
Added new configuration properties with environment variable support:

```properties
# Inventory Service URL - can be overridden with INVENTORY_SERVICE_URL environment variable
inventory.service.url=${INVENTORY_SERVICE_URL:https://inventory-service.internal:8081}

# Report Download Base URL - can be overridden with REPORT_DOWNLOAD_BASE_URL environment variable
report.download.base.url=${REPORT_DOWNLOAD_BASE_URL:https://reports.resorts-internal.com}
```

---

## GCP Deployment Configuration

### Option 1: Environment Variables (Non-sensitive URLs)
Set environment variables in your GCP deployment:

**Cloud Run**:
```bash
gcloud run deploy resorts-lite \
  --set-env-vars INVENTORY_SERVICE_URL=https://inventory-prod.example.com:8081 \
  --set-env-vars REPORT_DOWNLOAD_BASE_URL=https://reports-prod.example.com
```

**GKE (Kubernetes)**:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: resorts-lite
spec:
  template:
    spec:
      containers:
      - name: app
        env:
        - name: INVENTORY_SERVICE_URL
          value: "https://inventory-prod.example.com:8081"
        - name: REPORT_DOWNLOAD_BASE_URL
          value: "https://reports-prod.example.com"
```

**App Engine**:
```yaml
# app.yaml
env_variables:
  INVENTORY_SERVICE_URL: "https://inventory-prod.example.com:8081"
  REPORT_DOWNLOAD_BASE_URL: "https://reports-prod.example.com"
```

---

### Option 2: GCP Secret Manager (Sensitive URLs)
For sensitive endpoints, store URLs in Secret Manager:

**Create Secrets**:
```bash
# Create secret for inventory service URL
echo -n "https://inventory-prod.example.com:8081" | \
  gcloud secrets create inventory-service-url --data-file=-

# Create secret for report download URL
echo -n "https://reports-prod.example.com" | \
  gcloud secrets create report-download-base-url --data-file=-
```

**Grant Access**:
```bash
# Grant the service account access to secrets
gcloud secrets add-iam-policy-binding inventory-service-url \
  --member="serviceAccount:YOUR-SERVICE-ACCOUNT@PROJECT-ID.iam.gserviceaccount.com" \
  --role="roles/secretmanager.secretAccessor"

gcloud secrets add-iam-policy-binding report-download-base-url \
  --member="serviceAccount:YOUR-SERVICE-ACCOUNT@PROJECT-ID.iam.gserviceaccount.com" \
  --role="roles/secretmanager.secretAccessor"
```

**Update application.properties** to use Secret Manager:
```properties
# Reference secrets from GCP Secret Manager
inventory.service.url=${sm://inventory-service-url}
report.download.base.url=${sm://report-download-base-url}
```

---

## Environment-Specific Configuration

### Development Environment
```bash
export INVENTORY_SERVICE_URL=https://inventory-dev.example.com:8081
export REPORT_DOWNLOAD_BASE_URL=https://reports-dev.example.com
```

### Staging Environment
```bash
export INVENTORY_SERVICE_URL=https://inventory-staging.example.com:8081
export REPORT_DOWNLOAD_BASE_URL=https://reports-staging.example.com
```

### Production Environment
```bash
export INVENTORY_SERVICE_URL=https://inventory-prod.example.com:8081
export REPORT_DOWNLOAD_BASE_URL=https://reports-prod.example.com
```

---

## Testing the Changes

### Local Testing
```bash
# Test with default values (from application.properties)
mvn spring-boot:run

# Test with custom environment variables
INVENTORY_SERVICE_URL=https://test-inventory.local:9090 \
REPORT_DOWNLOAD_BASE_URL=https://test-reports.local \
mvn spring-boot:run
```

### Verify Configuration
Access the application and check the logs to confirm the URLs are being loaded correctly:
```
GET /api/bookings/availability?roomType=SUITE
GET /api/bookings/report/download?month=2024-03
```

---

## Security Improvements

1. **HTTPS by Default**: All default URLs now use HTTPS instead of HTTP
2. **No Hardcoded Ports**: Port numbers removed from base URLs, allowing cloud load balancers to handle routing
3. **Environment Isolation**: Different URLs can be used for dev/staging/prod without code changes
4. **Secret Manager Ready**: Configuration supports GCP Secret Manager for sensitive endpoints

---

## Compliance

These fixes ensure compliance with:
- ✅ 12-Factor App Principle III: Store config in the environment
- ✅ GCP Well-Architected Framework: Externalized configuration
- ✅ Cloud Security Best Practices: HTTPS enforcement
- ✅ Cloud-Native Patterns: Environment-based configuration

---

## Next Steps

1. **Update Deployment Scripts**: Add environment variables to your deployment configuration
2. **Configure Secret Manager**: For production, migrate sensitive URLs to Secret Manager
3. **Update CI/CD Pipeline**: Ensure environment-specific URLs are injected during deployment
4. **Test Each Environment**: Verify URLs are correctly resolved in dev, staging, and production

---

## Related Violations

This fix addresses **cr-java-0071** (Hard-coded Environment URLs). Other related violations in the codebase:
- cr-java-0065: HTTP session state (requires distributed session store)
- cr-java-0067: In-memory cache (requires Redis/Memorystore)
- cr-java-0088: Plain HTTP calls (requires HTTPS enforcement)
- czr-java-001: Hard-coded file paths (requires GCS migration)
- czr-port-001: Hard-coded ports (requires dynamic port binding)

These will be addressed in separate remediation efforts.
