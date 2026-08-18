# AWS Secrets Manager Configuration

## Overview
This application has been updated to use AWS Secrets Manager for secure credential management instead of hard-coded database credentials.

## AWS Secrets Manager Setup

### 1. Create Secret in AWS Secrets Manager

Create a secret in AWS Secrets Manager with the following JSON structure:

```json
{
  "DB_HOST": "db-prod.resorts-internal.com",
  "DB_USER": "admin",
  "DB_PASS": "your-secure-password"
}
```

### 2. AWS CLI Command to Create Secret

```bash
aws secretsmanager create-secret \
    --name resorts-db-credentials \
    --description "Database credentials for ResortsLite application" \
    --secret-string '{"DB_HOST":"db-prod.resorts-internal.com","DB_USER":"admin","DB_PASS":"your-secure-password"}' \
    --region us-east-1
```

### 3. IAM Permissions Required

The application requires the following IAM permissions to access AWS Secrets Manager:

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
      "Resource": "arn:aws:secretsmanager:us-east-1:*:secret:resorts-db-credentials-*"
    }
  ]
}
```

### 4. Environment Variables

Configure the following environment variables:

- `AWS_SECRET_NAME`: Name of the secret in AWS Secrets Manager (default: `resorts-db-credentials`)
- `AWS_REGION`: AWS region where the secret is stored (default: `us-east-1`)

### 5. Local Development

For local development, you can use environment variables as fallback:

```bash
export DB_HOST=localhost
export DB_USER=sa
export DB_PASS=
```

## Benefits

1. **Security**: Credentials are no longer hard-coded in source code or version control
2. **Rotation**: AWS Secrets Manager supports automatic credential rotation
3. **Audit**: All secret access is logged in AWS CloudTrail
4. **Encryption**: Secrets are encrypted at rest using AWS KMS
5. **Cloud-Native**: Follows AWS best practices for credential management

## Migration Notes

- Removed hard-coded credentials from `BookingService.java` (lines 22-23)
- Added `SecretsManagerConfig` class to retrieve secrets from AWS Secrets Manager
- Added AWS Secrets Manager SDK dependency to `pom.xml`
- Updated `application.properties` with AWS Secrets Manager configuration
- Application now retrieves credentials at startup from AWS Secrets Manager
- Fallback to environment variables if Secrets Manager is unavailable (for local development)
