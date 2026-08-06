# AWS Cognito Authentication Integration

## Overview

**FIXED [cr-java-0090]: File-based Authentication**

This application has been migrated from file-based authentication to AWS Cognito for cloud-native identity and access management. This provides:

- **Centralized Authentication**: Single source of truth for user identities across distributed cloud environments
- **Enhanced Security**: Built-in encryption, MFA support, and password policies
- **Scalability**: Handles millions of users without infrastructure management
- **Audit Logging**: Integration with AWS CloudTrail for compliance and security monitoring
- **User Lifecycle Management**: Built-in features for user registration, password reset, and account recovery

## Architecture

### Components

1. **AwsCognitoConfig**: Configuration class that initializes AWS Cognito Identity Provider client
2. **BookingService**: Enhanced with authentication methods using AWS Cognito
3. **AuthenticationController**: REST API endpoints for user authentication operations
4. **AwsSecretsManagerConfig**: Manages database credentials securely (separate from user authentication)

### Authentication Flow

```
User → AuthenticationController → BookingService → AwsCognitoConfig → AWS Cognito User Pool
                                                                              ↓
                                                                    JWT Tokens (Access, ID, Refresh)
```

## Configuration

### Environment Variables

Set the following environment variables for AWS Cognito integration:

```bash
# AWS Cognito Configuration
export AWS_COGNITO_USER_POOL_ID=us-east-1_XXXXXXXXX
export AWS_COGNITO_CLIENT_ID=your-client-id-here
export AWS_REGION=us-east-1

# AWS Credentials (for SDK authentication)
export AWS_ACCESS_KEY_ID=your-access-key
export AWS_SECRET_ACCESS_KEY=your-secret-key
```

### Application Properties

Configuration is defined in `application.properties`:

```properties
# AWS Cognito Configuration
aws.cognito.user-pool-id=${AWS_COGNITO_USER_POOL_ID:}
aws.cognito.client-id=${AWS_COGNITO_CLIENT_ID:}
aws.cognito.region=${AWS_REGION:us-east-1}
```

## AWS Cognito Setup

### 1. Create User Pool

```bash
# Create Cognito User Pool
aws cognito-idp create-user-pool \
  --pool-name resorts-lite-users \
  --policies "PasswordPolicy={MinimumLength=8,RequireUppercase=true,RequireLowercase=true,RequireNumbers=true,RequireSymbols=false}" \
  --auto-verified-attributes email \
  --username-attributes email \
  --mfa-configuration OFF \
  --region us-east-1

# Note the UserPoolId from the response
```

### 2. Create App Client

```bash
# Create App Client for the User Pool
aws cognito-idp create-user-pool-client \
  --user-pool-id us-east-1_XXXXXXXXX \
  --client-name resorts-lite-app \
  --explicit-auth-flows ALLOW_USER_PASSWORD_AUTH ALLOW_REFRESH_TOKEN_AUTH ALLOW_USER_SRP_AUTH \
  --generate-secret false \
  --region us-east-1

# Note the ClientId from the response
```

### 3. Create Test User

```bash
# Create a test user
aws cognito-idp admin-create-user \
  --user-pool-id us-east-1_XXXXXXXXX \
  --username testuser@example.com \
  --user-attributes Name=email,Value=testuser@example.com Name=email_verified,Value=true \
  --temporary-password TempPass123! \
  --message-action SUPPRESS \
  --region us-east-1

# Set permanent password
aws cognito-idp admin-set-user-password \
  --user-pool-id us-east-1_XXXXXXXXX \
  --username testuser@example.com \
  --password SecurePass123! \
  --permanent \
  --region us-east-1
```

## API Endpoints

### Authentication Endpoints

#### 1. Login (Authenticate User)

```bash
POST /api/auth/login
Content-Type: application/json

{
  "username": "testuser@example.com",
  "password": "SecurePass123!"
}

Response:
{
  "success": true,
  "accessToken": "eyJraWQiOiJ...",
  "idToken": "eyJraWQiOiJ...",
  "refreshToken": "eyJjdHkiOiJ...",
  "tokenType": "Bearer",
  "expiresIn": "3600",
  "message": "Authentication successful"
}
```

#### 2. Verify Token

```bash
POST /api/auth/verify
Content-Type: application/json

{
  "accessToken": "eyJraWQiOiJ..."
}

Response:
{
  "valid": true,
  "username": "testuser@example.com",
  "email": "testuser@example.com",
  "attributes": {
    "username": "testuser@example.com",
    "email": "testuser@example.com",
    "email_verified": "true"
  }
}
```

#### 3. Register User

```bash
POST /api/auth/register
Content-Type: application/json

{
  "username": "newuser@example.com",
  "password": "SecurePass123!",
  "email": "newuser@example.com",
  "given_name": "John",
  "family_name": "Doe"
}

Response:
{
  "success": true,
  "username": "newuser@example.com",
  "message": "User created successfully"
}
```

#### 4. Get User Details

```bash
GET /api/auth/user/testuser@example.com

Response:
{
  "success": true,
  "username": "testuser@example.com",
  "userStatus": "CONFIRMED",
  "enabled": "true",
  "details": {
    "username": "testuser@example.com",
    "email": "testuser@example.com",
    "email_verified": "true",
    "userStatus": "CONFIRMED",
    "enabled": "true"
  }
}
```

#### 5. Change Password

```bash
POST /api/auth/change-password
Content-Type: application/json

{
  "accessToken": "eyJraWQiOiJ...",
  "oldPassword": "OldPass123!",
  "newPassword": "NewPass123!"
}

Response:
{
  "success": true,
  "message": "Password changed successfully"
}
```

#### 6. Logout

```bash
POST /api/auth/logout
Content-Type: application/json

{
  "accessToken": "eyJraWQiOiJ..."
}

Response:
{
  "success": true,
  "message": "Signed out successfully"
}
```

#### 7. Health Check

```bash
GET /api/auth/health

Response:
{
  "status": "UP",
  "service": "AWS Cognito Authentication",
  "userPoolId": "us-east-1_XXXXXXXXX",
  "clientId": "your-client-id",
  "configured": true
}
```

## Integration with Existing Endpoints

### Protected Endpoints

To protect existing endpoints, add token verification:

```java
@GetMapping("/api/bookings/{id}")
public ResponseEntity<Map<String, Object>> getBooking(
    @PathVariable String id,
    @RequestHeader("Authorization") String authHeader) {
    
    // Extract token from "Bearer <token>"
    String token = authHeader.replace("Bearer ", "");
    
    // Verify token
    Map<String, Object> verification = bookingService.verifyUserToken(token);
    if (!Boolean.TRUE.equals(verification.get("valid"))) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(Map.of("error", "Invalid or expired token"));
    }
    
    // Process request
    Map<String, Object> booking = bookingService.getBookingById(id);
    return ResponseEntity.ok(booking);
}
```

## Security Best Practices

### 1. Token Storage
- Store tokens securely on the client side (e.g., HttpOnly cookies, secure storage)
- Never store tokens in localStorage for sensitive applications
- Use refresh tokens to obtain new access tokens

### 2. Token Validation
- Always validate access tokens on the server side
- Check token expiration
- Verify token signature using Cognito public keys

### 3. Password Policies
- Enforce strong password requirements in Cognito User Pool
- Enable MFA for sensitive operations
- Implement account lockout policies

### 4. HTTPS Only
- Always use HTTPS in production
- Enable SSL/TLS for all authentication endpoints
- Use secure cookies with SameSite attribute

## Migration from File-based Authentication

### Before (File-based)
```java
// Old approach - storing credentials in files
Properties userProps = new Properties();
userProps.load(new FileInputStream("users.properties"));
String storedPassword = userProps.getProperty(username);
if (password.equals(storedPassword)) {
    // Authenticate user
}
```

### After (AWS Cognito)
```java
// New approach - using AWS Cognito
Map<String, Object> result = bookingService.authenticateUser(username, password);
if (Boolean.TRUE.equals(result.get("success"))) {
    String accessToken = (String) result.get("accessToken");
    // User authenticated successfully
}
```

## Benefits of AWS Cognito

1. **Scalability**: Automatically scales to handle millions of users
2. **Security**: Built-in encryption, MFA, and advanced security features
3. **Compliance**: Meets HIPAA, SOC, ISO, and PCI DSS compliance requirements
4. **User Management**: Built-in user registration, password reset, and account recovery
5. **Integration**: Easy integration with AWS services (API Gateway, Lambda, etc.)
6. **Cost-effective**: Pay only for active users
7. **Monitoring**: Integration with CloudWatch and CloudTrail for monitoring and auditing

## Troubleshooting

### Common Issues

1. **Invalid User Pool ID or Client ID**
   - Verify environment variables are set correctly
   - Check AWS region matches User Pool region

2. **Authentication Fails**
   - Verify user exists in User Pool
   - Check password meets policy requirements
   - Ensure user status is CONFIRMED

3. **Token Verification Fails**
   - Check token hasn't expired (default: 1 hour)
   - Verify token is valid JWT format
   - Ensure token was issued by correct User Pool

4. **AWS Credentials Error**
   - Verify AWS credentials are configured
   - Check IAM permissions for Cognito operations
   - Ensure DefaultCredentialsProvider can find credentials

## Dependencies

The following dependencies are required in `pom.xml`:

```xml
<!-- AWS Cognito Identity Provider -->
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>cognitoidentityprovider</artifactId>
    <version>2.20.26</version>
</dependency>

<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>cognitoidentity</artifactId>
    <version>2.20.26</version>
</dependency>
```

## Additional Resources

- [AWS Cognito Documentation](https://docs.aws.amazon.com/cognito/)
- [AWS SDK for Java v2 - Cognito](https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/cognitoidentityprovider/package-summary.html)
- [Spring Security with AWS Cognito](https://docs.spring.io/spring-security/reference/servlet/oauth2/index.html)
- [AWS Cognito Best Practices](https://docs.aws.amazon.com/cognito/latest/developerguide/security-best-practices.html)
