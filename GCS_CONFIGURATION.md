# Google Cloud Storage Configuration Guide

## Overview
The ResortsLite application has been migrated to use Google Cloud Storage (GCS) for report storage instead of local filesystem operations. This ensures data persistence across container restarts and scaling events.

## Required Environment Variables

### GCS_BUCKET_NAME
- **Description**: The name of the GCS bucket where reports will be stored
- **Default**: `resort-reports-bucket`
- **Example**: `my-company-resort-reports`
- **Required**: Yes (for production)

### GCS_REPORT_PREFIX
- **Description**: The prefix/folder path for report files within the bucket
- **Default**: `reports/`
- **Example**: `monthly-reports/` or `reports/2024/`
- **Required**: No (uses default if not set)

### GCS_BACKUP_PREFIX
- **Description**: The prefix/folder path for backup files within the bucket
- **Default**: `backups/nightly/`
- **Example**: `backups/daily/` or `archive/backups/`
- **Required**: No (uses default if not set)

## GCP Authentication

The application uses Google Cloud's default authentication mechanism. Ensure one of the following is configured:

### Option 1: Service Account Key (Development)
```bash
export GOOGLE_APPLICATION_CREDENTIALS="/path/to/service-account-key.json"
```

### Option 2: Workload Identity (GKE - Recommended for Production)
Configure Workload Identity for your GKE cluster and bind the Kubernetes service account to a GCP service account with Storage Object Admin permissions.

### Option 3: Compute Engine Default Service Account
When running on GCE, GKE, or Cloud Run, the default service account is automatically used.

## Required GCP Permissions

The service account used by the application must have the following IAM permissions:

- `storage.objects.create` - To upload reports
- `storage.objects.get` - To read reports
- `storage.objects.list` - To list reports (if needed)
- `storage.buckets.get` - To access bucket metadata

**Recommended Role**: `roles/storage.objectAdmin` on the specific bucket

## Setting Up the GCS Bucket

```bash
# Create the bucket
gsutil mb -p YOUR_PROJECT_ID -c STANDARD -l us-central1 gs://resort-reports-bucket

# Set lifecycle policy (optional - auto-delete old reports after 90 days)
cat > lifecycle.json << 'LIFECYCLE'
{
  "lifecycle": {
    "rule": [
      {
        "action": {"type": "Delete"},
        "condition": {"age": 90}
      }
    ]
  }
}
LIFECYCLE

gsutil lifecycle set lifecycle.json gs://resort-reports-bucket

# Grant permissions to service account
gsutil iam ch serviceAccount:YOUR_SERVICE_ACCOUNT@YOUR_PROJECT.iam.gserviceaccount.com:roles/storage.objectAdmin gs://resort-reports-bucket
```

## Deployment Configuration Examples

### Kubernetes Deployment
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: resortslite
spec:
  template:
    spec:
      serviceAccountName: resortslite-sa  # Bound to GCP SA via Workload Identity
      containers:
      - name: resortslite
        image: gcr.io/YOUR_PROJECT/resortslite:latest
        env:
        - name: GCS_BUCKET_NAME
          value: "resort-reports-bucket"
        - name: GCS_REPORT_PREFIX
          value: "reports/"
        - name: GCS_BACKUP_PREFIX
          value: "backups/nightly/"
```

### Cloud Run
```bash
gcloud run deploy resortslite \
  --image gcr.io/YOUR_PROJECT/resortslite:latest \
  --set-env-vars GCS_BUCKET_NAME=resort-reports-bucket,GCS_REPORT_PREFIX=reports/,GCS_BACKUP_PREFIX=backups/nightly/ \
  --service-account YOUR_SERVICE_ACCOUNT@YOUR_PROJECT.iam.gserviceaccount.com
```

### Docker Compose (Development)
```yaml
version: '3.8'
services:
  resortslite:
    image: resortslite:latest
    environment:
      - GCS_BUCKET_NAME=resort-reports-bucket
      - GCS_REPORT_PREFIX=reports/
      - GCS_BACKUP_PREFIX=backups/nightly/
      - GOOGLE_APPLICATION_CREDENTIALS=/app/credentials/key.json
    volumes:
      - ./service-account-key.json:/app/credentials/key.json:ro
```

## Testing the Configuration

### Verify GCS Access
```bash
# Test bucket access
gsutil ls gs://resort-reports-bucket

# Test write permissions
echo "test" | gsutil cp - gs://resort-reports-bucket/test.txt

# Clean up test file
gsutil rm gs://resort-reports-bucket/test.txt
```

### Application Health Check
After deployment, verify the application can access GCS:

```bash
# Check system info endpoint
curl http://localhost:8080/api/bookings/system-info

# Expected response should include:
# {
#   "gcsBucket": "resort-reports-bucket",
#   "gcsReportPrefix": "reports/",
#   "gcsBackupPrefix": "backups/nightly/",
#   ...
# }
```

## Troubleshooting

### Error: "The Application Default Credentials are not available"
**Solution**: Set up authentication using one of the methods described above.

### Error: "403 Forbidden" when uploading to GCS
**Solution**: Verify the service account has the required permissions on the bucket.

### Error: "404 Not Found" - Bucket does not exist
**Solution**: Create the bucket or verify the bucket name is correct.

### Reports not persisting after container restart
**Solution**: Verify the application is using GCS (check logs for "Report successfully uploaded to GCS" messages).

## Migration Notes

### What Changed
- **Before**: Reports were written to local filesystem paths (e.g., `/var/reports/`, `C:\reports\`)
- **After**: Reports are uploaded to Google Cloud Storage with durable persistence

### Benefits
1. **Durability**: Reports survive container restarts and pod rescheduling
2. **Scalability**: Multiple instances can write to the same bucket without conflicts
3. **Accessibility**: Reports can be accessed from anywhere with proper credentials
4. **Cost-effective**: Pay only for storage used, with automatic lifecycle management
5. **Cloud-native**: Follows 12-factor app principles for stateless applications

### Backward Compatibility
Local filesystem paths are no longer used. If you need to access reports locally, use `gsutil` to download them:

```bash
gsutil cp gs://resort-reports-bucket/reports/resort_report_march_2024.csv ./local-reports/
```

## Monitoring and Logging

The application logs GCS operations:
- Successful uploads: `INFO: Report successfully uploaded to GCS: gs://bucket/path`
- Failures: `SEVERE: Failed to upload report to GCS: error message`

Monitor these logs to ensure reports are being stored correctly.

## Cost Considerations

- **Storage**: Standard storage costs ~$0.020 per GB per month
- **Operations**: Class A operations (uploads) cost ~$0.05 per 10,000 operations
- **Data Transfer**: Egress charges apply when downloading reports outside GCP

For a typical application generating 100 reports/day at 1MB each:
- Monthly storage: ~3GB = $0.06
- Monthly operations: ~3,000 uploads = $0.015
- **Total**: ~$0.08/month (excluding egress)

## Security Best Practices

1. **Use Workload Identity**: Avoid storing service account keys in containers
2. **Principle of Least Privilege**: Grant only necessary permissions (objectAdmin on specific bucket)
3. **Enable Bucket Versioning**: Protect against accidental deletions
4. **Use VPC Service Controls**: Restrict bucket access to specific networks
5. **Enable Audit Logging**: Track all access to reports for compliance

## Support

For issues or questions:
1. Check application logs for error messages
2. Verify GCS bucket permissions and configuration
3. Test GCS access using `gsutil` commands
4. Review GCP IAM policies for the service account
