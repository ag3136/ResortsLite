# AWS Secrets Manager Setup Guide

## Overview
This application has been updated to use AWS Secrets Manager for secure database credential management, replacing hard-coded credentials that were previously embedded in the source code.

## What Was Fixed
- **Rule ID**: cr-java-0069 - Hard-coded Database Credentials
- **Severity**: CRITICAL
- **Files Modified**:
  - `BookingService.java` (Lines 22-23): Removed hard-coded DB_USER and DB_PASS constants
  - Added `AwsSecretsManagerConfig.java`: New configuration class for AWS Secrets Manager integration
  - Updated `pom.xml`: Added AWS Secrets Manager SDK dependency
  - Updated `application.properties`: Added AWS Secrets Manager configuration

## AWS Secrets Manager Setup

### 1. Create Secret in AWS Secrets Manager

Using AWS CLI:
```bash
aws secretsmanager create-secret \
    --name resorts-lite/db-credentials \
    --description "Database credentials for ResortsLite application" \
    --secret-string '{
        "host": "db-prod.resorts-internal.com",
        "username": "admin",
        "password": "your-secure-password-here",
        "database": "resortdb",
        "port": "3306"
    }' \
    --region us-east-1
```

Using AWS Console:
1. Navigate to AWS Secrets Manager in the AWS Console
2. Click "Store a new secret"
3. Select "Other type of secret"
4. Add the following key-value pairs:
   - `host`: Your database hostname
   - `username`: Database username
   - `password`: Database password
   - `database`: Database name
   - `port`: Database port
5. Name the secret: `resorts-lite/db-credentials`
6. Complete the wizard with default settings

### 2. Configure IAM Permissions

Your application's IAM role (EC2 instance role, ECS task role, or Lambda execution role) needs permission to read the secret:

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "secretsmanager:GetSecretValue",
                "secretsmanager:DescribeSecret"
            ],
            "Resource": "arn:aws:secretsmanager:us-east-1:ACCOUNT_ID:secret:resorts-lite/db-credentials-*"
        }
    ]
}
```

### 3. Environment Variables

Set the following environment variables (optional - defaults are provided):

```bash
export AWS_SECRET_NAME=resorts-lite/db-credentials
export AWS_REGION=us-east-1
```

Or configure in your deployment:
- **ECS Task Definition**: Add environment variables in the container definition
- **Kubernetes**: Use ConfigMap or environment variables in the pod spec
- **EC2**: Set in user data or application startup script

### 4. Local Development

For local development without AWS credentials, the application will fall back to environment variables:

```bash
export DB_HOST=localhost
export DB_USER=sa
export DB_PASS=
export DB_NAME=resortdb
export DB_PORT=3306
```

## How It Works

1. **Application Startup**: `AwsSecretsManagerConfig` is initialized as a Spring bean
2. **Credential Retrieval**: The `@PostConstruct` method retrieves credentials from AWS Secrets Manager
3. **Fallback Mechanism**: If Secrets Manager is unavailable (e.g., local dev), it falls back to environment variables
4. **Usage**: `BookingService` autowires `AwsSecretsManagerConfig` and calls getter methods to access credentials

## Benefits

✅ **Security**: Credentials are no longer in source code or version control
✅ **Rotation**: Supports automatic credential rotation without code changes
✅ **Encryption**: Credentials are encrypted at rest using AWS KMS
✅ **Audit**: All secret access is logged in AWS CloudTrail
✅ **Compliance**: Meets cloud security compliance requirements

## Credential Rotation

To rotate credentials:

1. Update the secret in AWS Secrets Manager:
```bash
aws secretsmanager update-secret \
    --secret-id resorts-lite/db-credentials \
    --secret-string '{
        "host": "db-prod.resorts-internal.com",
        "username": "admin",
        "password": "new-secure-password",
        "database": "resortdb",
        "port": "3306"
    }'
```

2. Restart the application or call the `refreshCredentials()` method to reload

## Troubleshooting

### Error: "Unable to retrieve credentials from AWS Secrets Manager"
- **Cause**: IAM permissions missing or secret doesn't exist
- **Solution**: Verify IAM role has `secretsmanager:GetSecretValue` permission and secret exists

### Error: "Access Denied"
- **Cause**: IAM role lacks permission to access the secret
- **Solution**: Add the IAM policy shown in section 2 above

### Application uses wrong credentials
- **Cause**: Secret name mismatch or wrong AWS region
- **Solution**: Verify `AWS_SECRET_NAME` and `AWS_REGION` environment variables

## Migration Checklist

- [x] Remove hard-coded credentials from source code
- [x] Add AWS Secrets Manager SDK dependency
- [x] Create `AwsSecretsManagerConfig` configuration class
- [x] Update `BookingService` to use Secrets Manager
- [x] Update `application.properties` with Secrets Manager config
- [ ] Create secret in AWS Secrets Manager
- [ ] Configure IAM permissions for application role
- [ ] Test application with Secrets Manager integration
- [ ] Remove old hard-coded credentials from git history (optional but recommended)

## Additional Resources

- [AWS Secrets Manager Documentation](https://docs.aws.amazon.com/secretsmanager/)
- [AWS SDK for Java v2 - Secrets Manager](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/examples-secretsmanager.html)
- [Rotating AWS Secrets Manager Secrets](https://docs.aws.amazon.com/secretsmanager/latest/userguide/rotating-secrets.html)
