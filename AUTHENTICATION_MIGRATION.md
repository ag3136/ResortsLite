# Authentication Migration: File-based to AWS Cognito + Secrets Manager

## Overview
This document describes the migration from file-based authentication to cloud-native authentication using AWS Cognito and AWS Secrets Manager.

## Problem Statement (cr-java-0090)
**Rule**: File-based Authentication  
**Severity**: HIGH  
**Category**: Security & Authentication

The application previously stored authentication credentials, user data, or security tokens in local files or hardcoded in source code. This approach:
- Does not scale horizontally in cloud environments
- Creates security vulnerabilities (credentials in source control)
- Causes consistency issues in distributed deployments
- Lacks centralized credential rotation capabilities
- Does not support modern authentication patterns (OAuth, SAML, MFA)

## Solution Architecture

### 1. AWS Secrets Manager Integration
**Purpose**: Secure storage and retrieval of database credentials and API keys

**Implementation**:
- `SecretsManagerConfig.java`: Configuration class that retrieves secrets from AWS Secrets Manager
- Secrets are loaded at application startup via `@PostConstruct`
- Fallback to environment variables for local development
- Supports automatic credential rotation

**Configuration**:
```properties
aws.secretsmanager.secret.name=${AWS_SECRET_NAME:resorts-db-credentials}
aws.region=${AWS_REGION:us-east-1}
```

**Usage in BookingService**:
```java
@Autowired
private SecretsManagerConfig secretsManagerConfig;

// Access credentials securely
String dbHost = secretsManagerConfig.getDbHost();
String dbUser = secretsManagerConfig.getDbUser();
String dbPassword = secretsManagerConfig.getDbPassword();
```

### 2. Amazon Cognito Integration
**Purpose**: Cloud-native user identity and authentication management

**Implementation**:
- `CognitoAuthenticationConfig.java`: Configuration for Cognito User Pool connection
- `CognitoAuthenticationService.java`: Service for user authentication operations

**Features**:
- User registration and sign-up
- User authentication with JWT token issuance
- Token validation and refresh
- User sign-out and session management
- Built-in password policies and MFA support
- Integration with AWS IAM for fine-grained access control

**Configuration**:
```properties
aws.cognito.userPoolId=${AWS_COGNITO_USER_POOL_ID:}
aws.cognito.clientId=${AWS_COGNITO_CLIENT_ID:}
```

**Authentication Flow**:
1. User submits credentials to `CognitoAuthenticationService.authenticateUser()`
2. Service calls AWS Cognito User Pool for authentication
3. Cognito validates credentials and returns JWT tokens (access, ID, refresh)
4. Application uses access token for subsequent API calls
5. Token validation performed via `CognitoAuthenticationService.validateToken()`

## Migration Steps Completed

### Step 1: Remove Hardcoded Credentials ✅
**Before**:
```java
private static final String DB_USER = "admin";
private static final String DB_PASS = "Resort$Pass#2019!";
```

**After**:
```java
@Autowired
private SecretsManagerConfig secretsManagerConfig;
// Credentials retrieved from AWS Secrets Manager
```

### Step 2: Add AWS SDK Dependencies ✅
Added to `pom.xml`:
- `software.amazon.awssdk:secretsmanager` - For credential management
- `software.amazon.awssdk:cognitoidentityprovider` - For user authentication

### Step 3: Create Configuration Classes ✅
- `SecretsManagerConfig.java` - Manages secret retrieval
- `CognitoAuthenticationConfig.java` - Configures Cognito client

### Step 4: Implement Authentication Service ✅
- `CognitoAuthenticationService.java` - Provides authentication operations:
  - `authenticateUser()` - User login
  - `validateToken()` - Token validation
  - `registerUser()` - User registration
  - `signOutUser()` - User logout

### Step 5: Update Application Configuration ✅
Added to `application.properties`:
- AWS Secrets Manager configuration
- AWS Cognito User Pool configuration
- Environment variable support for cloud deployment

## Benefits of Cloud-Native Authentication

### Security
- ✅ No credentials in source code or configuration files
- ✅ Centralized credential management with AWS Secrets Manager
- ✅ Automatic credential rotation support
- ✅ Encrypted storage of user credentials in Cognito
- ✅ Built-in password policies and complexity requirements
- ✅ Multi-factor authentication (MFA) support

### Scalability
- ✅ Horizontal scaling without session affinity issues
- ✅ Distributed authentication across multiple instances
- ✅ No local file dependencies
- ✅ Stateless authentication with JWT tokens

### Compliance
- ✅ Audit trail of authentication events in CloudWatch
- ✅ Compliance with security best practices (OWASP, NIST)
- ✅ Support for regulatory requirements (GDPR, HIPAA)

### Operations
- ✅ Centralized user management
- ✅ Self-service password reset
- ✅ User lifecycle management (registration, verification, deletion)
- ✅ Integration with AWS CloudWatch for monitoring

## Deployment Configuration

### AWS Secrets Manager Setup
1. Create a secret in AWS Secrets Manager:
```bash
aws secretsmanager create-secret \
  --name resorts-db-credentials \
  --secret-string '{"DB_HOST":"db-prod.resorts.com","DB_USER":"admin","DB_PASS":"SecurePassword123!"}'
```

2. Grant IAM permissions to application:
```json
{
  "Effect": "Allow",
  "Action": [
    "secretsmanager:GetSecretValue",
    "secretsmanager:DescribeSecret"
  ],
  "Resource": "arn:aws:secretsmanager:us-east-1:*:secret:resorts-db-credentials-*"
}
```

### AWS Cognito Setup
1. Create a Cognito User Pool:
```bash
aws cognito-idp create-user-pool \
  --pool-name resorts-user-pool \
  --policies "PasswordPolicy={MinimumLength=8,RequireUppercase=true,RequireLowercase=true,RequireNumbers=true}"
```

2. Create an App Client:
```bash
aws cognito-idp create-user-pool-client \
  --user-pool-id <USER_POOL_ID> \
  --client-name resorts-app-client \
  --explicit-auth-flows USER_PASSWORD_AUTH
```

3. Set environment variables:
```bash
export AWS_COGNITO_USER_POOL_ID=us-east-1_XXXXXXXXX
export AWS_COGNITO_CLIENT_ID=XXXXXXXXXXXXXXXXXXXXXXXXXX
export AWS_SECRET_NAME=resorts-db-credentials
export AWS_REGION=us-east-1
```

## Testing

### Local Development
For local development without AWS services:
1. Set environment variables for database credentials
2. Cognito authentication will fail gracefully
3. Consider using Cognito Local for testing

### Integration Testing
1. Create a test user in Cognito User Pool
2. Test authentication flow:
```java
Map<String, String> result = cognitoAuthenticationService.authenticateUser("testuser", "TestPass123!");
String accessToken = result.get("accessToken");
```

3. Validate token:
```java
Map<String, Object> validation = cognitoAuthenticationService.validateToken(accessToken);
boolean isValid = (boolean) validation.get("valid");
```

## Monitoring and Troubleshooting

### CloudWatch Logs
- Cognito authentication events logged to CloudWatch
- Secrets Manager access logged for audit

### Common Issues
1. **Invalid credentials**: Check Secrets Manager secret format
2. **Cognito authentication fails**: Verify User Pool ID and Client ID
3. **Token validation fails**: Check token expiration and format

### Metrics to Monitor
- Authentication success/failure rate
- Token validation latency
- Secrets Manager API call count
- Cognito User Pool active users

## Next Steps

### Recommended Enhancements
1. Implement JWT token validation in API endpoints
2. Add Spring Security integration with Cognito
3. Implement refresh token rotation
4. Add MFA support for sensitive operations
5. Integrate with AWS CloudWatch for authentication metrics
6. Implement rate limiting for authentication attempts

### Security Hardening
1. Enable MFA for all users
2. Implement account lockout policies
3. Add IP-based access restrictions
4. Enable advanced security features in Cognito
5. Implement token revocation mechanism

## References
- [AWS Secrets Manager Documentation](https://docs.aws.amazon.com/secretsmanager/)
- [Amazon Cognito Documentation](https://docs.aws.amazon.com/cognito/)
- [AWS SDK for Java v2](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/)
- [Spring Security with Cognito](https://docs.spring.io/spring-security/reference/servlet/oauth2/index.html)
