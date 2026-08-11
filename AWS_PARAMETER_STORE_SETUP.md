# AWS Systems Manager Parameter Store Configuration

## Overview
This application has been updated to use AWS Systems Manager Parameter Store for externalized environment-specific URLs, replacing hard-coded endpoints to enable cloud-native, environment-agnostic deployments.

## Required Parameters

The following parameters must be configured in AWS Systems Manager Parameter Store before deploying the application:

### 1. Inventory Service URL
- **Parameter Name**: `/resortslite/inventory/service/url`
- **Type**: String or SecureString
- **Description**: URL endpoint for the inventory service
- **Example Value**: `https://inventory-service.internal:8081/rooms/available`
- **Used By**: `BookingController.checkAvailability()`

### 2. Reports Service URL
- **Parameter Name**: `/resortslite/reports/service/url`
- **Type**: String or SecureString
- **Description**: Base URL endpoint for the reports download service
- **Example Value**: `https://reports.resorts-internal.com:8080/download`
- **Used By**: `ReportService.buildReportDownloadUrl()`

## Setup Instructions

### Using AWS CLI

```bash
# Set the inventory service URL
aws ssm put-parameter \
    --name "/resortslite/inventory/service/url" \
    --value "https://inventory-service.internal:8081/rooms/available" \
    --type "String" \
    --description "Inventory service endpoint for ResortsLite application" \
    --region us-east-1

# Set the reports service URL
aws ssm put-parameter \
    --name "/resortslite/reports/service/url" \
    --value "https://reports.resorts-internal.com:8080/download" \
    --type "String" \
    --description "Reports download service endpoint for ResortsLite application" \
    --region us-east-1
```

### Using AWS Console

1. Navigate to AWS Systems Manager → Parameter Store
2. Click "Create parameter"
3. Enter the parameter name (e.g., `/resortslite/inventory/service/url`)
4. Select type: String or SecureString
5. Enter the parameter value
6. Add description and tags as needed
7. Click "Create parameter"
8. Repeat for all required parameters

### Using Terraform

```hcl
resource "aws_ssm_parameter" "inventory_service_url" {
  name        = "/resortslite/inventory/service/url"
  description = "Inventory service endpoint for ResortsLite application"
  type        = "String"
  value       = "https://inventory-service.internal:8081/rooms/available"
  
  tags = {
    Application = "ResortsLite"
    Environment = var.environment
  }
}

resource "aws_ssm_parameter" "reports_service_url" {
  name        = "/resortslite/reports/service/url"
  description = "Reports download service endpoint for ResortsLite application"
  type        = "String"
  value       = "https://reports.resorts-internal.com:8080/download"
  
  tags = {
    Application = "ResortsLite"
    Environment = var.environment
  }
}
```

## IAM Permissions

The application's IAM role must have the following permissions to read parameters from Parameter Store:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ssm:GetParameter",
        "ssm:GetParameters"
      ],
      "Resource": [
        "arn:aws:ssm:*:*:parameter/resortslite/*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "kms:Decrypt"
      ],
      "Resource": [
        "arn:aws:kms:*:*:key/*"
      ],
      "Condition": {
        "StringEquals": {
          "kms:ViaService": [
            "ssm.*.amazonaws.com"
          ]
        }
      }
    }
  ]
}
```

## Environment-Specific Configuration

### Development Environment
```bash
aws ssm put-parameter --name "/resortslite/inventory/service/url" \
    --value "https://inventory-dev.internal:8081/rooms/available" \
    --type "String" --overwrite

aws ssm put-parameter --name "/resortslite/reports/service/url" \
    --value "https://reports-dev.resorts-internal.com:8080/download" \
    --type "String" --overwrite
```

### Staging Environment
```bash
aws ssm put-parameter --name "/resortslite/inventory/service/url" \
    --value "https://inventory-staging.internal:8081/rooms/available" \
    --type "String" --overwrite

aws ssm put-parameter --name "/resortslite/reports/service/url" \
    --value "https://reports-staging.resorts-internal.com:8080/download" \
    --type "String" --overwrite
```

### Production Environment
```bash
aws ssm put-parameter --name "/resortslite/inventory/service/url" \
    --value "https://inventory-prod.internal:8081/rooms/available" \
    --type "String" --overwrite

aws ssm put-parameter --name "/resortslite/reports/service/url" \
    --value "https://reports-prod.resorts-internal.com:8080/download" \
    --type "String" --overwrite
```

## Default Values

If a parameter is not found in Parameter Store, the application will use the following default values:

- **Inventory Service URL**: `https://inventory-service.internal:8081/rooms/available`
- **Reports Service URL**: `https://reports.resorts-internal.com:8080/download`

These defaults are configured in `application.properties`:
```properties
aws.paramstore.inventory.url.default=https://inventory-service.internal:8081/rooms/available
aws.paramstore.reports.url.default=https://reports.resorts-internal.com:8080/download
```

## Verification

To verify the parameters are correctly configured:

```bash
# List all ResortsLite parameters
aws ssm get-parameters-by-path \
    --path "/resortslite" \
    --recursive \
    --region us-east-1

# Get specific parameter value
aws ssm get-parameter \
    --name "/resortslite/inventory/service/url" \
    --region us-east-1
```

## Troubleshooting

### Parameter Not Found
If the application logs show "Could not retrieve parameter", verify:
1. The parameter exists in Parameter Store
2. The parameter name matches exactly (case-sensitive)
3. The IAM role has `ssm:GetParameter` permission
4. The AWS region is correctly configured

### Access Denied
If you receive access denied errors:
1. Check the IAM role attached to the EC2 instance or ECS task
2. Verify the IAM policy includes the required SSM permissions
3. If using SecureString, ensure KMS decrypt permissions are granted

### Wrong Region
Ensure the `AWS_REGION` environment variable is set correctly:
```bash
export AWS_REGION=us-east-1
```

Or configure it in `application.properties`:
```properties
aws.s3.region=us-east-1
```

## Benefits

✅ **Environment Agnostic**: Same application code works across dev, staging, and production  
✅ **No Code Changes**: Update URLs without redeploying the application  
✅ **Secure**: Use SecureString type for sensitive endpoints  
✅ **Centralized**: All configuration in one place  
✅ **Auditable**: Parameter changes are logged in CloudTrail  
✅ **Version Control**: Parameter Store maintains version history  

## Migration from Hard-Coded URLs

### Before (Hard-Coded)
```java
String inventoryUrl = "http://inventory-service.internal:8081/rooms/available";
```

### After (Externalized)
```java
String inventoryUrl = parameterStoreConfig.getParameter(inventoryUrlKey, inventoryUrlDefault);
```

This change enables:
- Dynamic URL configuration per environment
- HTTPS enforcement (default values use HTTPS)
- No application redeployment for URL changes
- Cloud-native configuration management
