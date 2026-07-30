# Azure Active Directory Authentication Setup

## Overview

This application has been migrated from file-based authentication to Azure Active Directory (Entra ID) authentication using Spring Security and Microsoft Authentication Library (MSAL).

## Fixed Issue: cr-java-0090 - File-based Authentication

**Previous Implementation:**
- No authentication mechanism
- Endpoints were publicly accessible
- Security risk: Anyone could access booking data

**Current Implementation:**
- Azure AD OAuth 2.0 / OpenID Connect authentication
- JWT token-based authentication (stateless)
- Role-based access control (RBAC) using Azure AD groups
- Centralized identity management
- Multi-factor authentication (MFA) support
- Single Sign-On (SSO) capability

## Azure AD Configuration Steps

### 1. Register Application in Azure AD

1. Navigate to Azure Portal > Azure Active Directory > App Registrations
2. Click "New registration"
3. Configure:
   - **Name**: ResortsLite
   - **Supported account types**: Accounts in this organizational directory only
   - **Redirect URI**: 
     - Type: Web
     - URI: `https://your-app-url.azurewebsites.net/login/oauth2/code/azure`
4. Click "Register"

### 2. Configure Application Settings

1. In the app registration, go to "Authentication"
2. Add additional redirect URIs if needed:
   - `http://localhost:8080/login/oauth2/code/azure` (for local development)
3. Enable "ID tokens" under Implicit grant and hybrid flows
4. Save changes

### 3. Create Client Secret

1. Go to "Certificates & secrets"
2. Click "New client secret"
3. Add description: "ResortsLite Production Secret"
4. Set expiration (recommended: 12 months)
5. Click "Add"
6. **IMPORTANT**: Copy the secret value immediately (it won't be shown again)
7. Store the secret in Azure Key Vault

### 4. Configure API Permissions

1. Go to "API permissions"
2. Add permissions:
   - Microsoft Graph > Delegated permissions:
     - `User.Read` (read user profile)
     - `email` (read user email)
     - `openid` (OpenID Connect sign-in)
     - `profile` (read user profile)
3. Click "Grant admin consent" (requires admin privileges)

### 5. Create Azure AD Groups for Authorization

1. Navigate to Azure Active Directory > Groups
2. Create groups:
   - **ResortsLite-Users**: Standard users who can create and view bookings
   - **ResortsLite-Admins**: Administrators who can download reports
3. Add users to appropriate groups

### 6. Configure Application ID URI

1. Go to "Expose an API"
2. Set Application ID URI: `api://resortslite` or use default `api://<client-id>`
3. Add scopes if needed for API access

## Environment Variables Configuration

Set the following environment variables in your Azure App Service or Container Apps:

```bash
# Azure AD Configuration
AZURE_AD_TENANT_ID=<your-tenant-id>
AZURE_AD_CLIENT_ID=<your-client-id>
AZURE_AD_CLIENT_SECRET=<your-client-secret>
AZURE_AD_APP_ID_URI=api://resortslite
AZURE_AD_ALLOWED_GROUPS=ResortsLite-Users,ResortsLite-Admins
AZURE_AD_POST_LOGOUT_URI=https://your-app-url.azurewebsites.net

# Other Azure Services
AZURE_KEYVAULT_URL=https://your-keyvault.vault.azure.net/
AZURE_REDIS_HOST=your-redis.redis.cache.windows.net
AZURE_REDIS_PORT=6380
AZURE_REDIS_PASSWORD=<redis-access-key>
AZURE_REDIS_SSL=true
```

### Storing Secrets in Azure Key Vault

For production, store sensitive values in Azure Key Vault:

```bash
# Store Azure AD client secret
az keyvault secret set --vault-name your-keyvault --name azure-ad-client-secret --value "<client-secret>"

# Store Redis password
az keyvault secret set --vault-name your-keyvault --name azure-redis-password --value "<redis-password>"

# Update application to read from Key Vault
AZURE_AD_CLIENT_SECRET=${@Microsoft.KeyVault(SecretUri=https://your-keyvault.vault.azure.net/secrets/azure-ad-client-secret/)}
```

## Authentication Flow

1. **User Access**: User navigates to protected endpoint (e.g., `/api/bookings/create`)
2. **Redirect to Azure AD**: Spring Security redirects to Azure AD login page
3. **User Authentication**: User enters credentials (username/password + MFA if enabled)
4. **Token Issuance**: Azure AD validates credentials and issues JWT access token
5. **Token Validation**: Application validates token signature and claims
6. **Access Granted**: User can access protected resources
7. **Subsequent Requests**: Token is included in Authorization header for API calls

## Protected Endpoints

### Public Endpoints (No Authentication Required)
- `GET /actuator/health` - Health check for load balancers
- `GET /h2-console/**` - H2 database console (development only)

### Authenticated Endpoints (Requires Azure AD Login)
- `POST /api/bookings/create` - Create new booking
- `GET /api/bookings/status/{bookingId}` - Get booking status
- `GET /api/bookings/availability` - Check room availability

### Admin-Only Endpoints (Requires ResortsLite-Admins Group)
- `GET /api/bookings/report/download` - Download booking reports

## Testing Authentication

### Local Development

1. Set environment variables in your IDE or terminal:
```bash
export AZURE_AD_TENANT_ID=your-tenant-id
export AZURE_AD_CLIENT_ID=your-client-id
export AZURE_AD_CLIENT_SECRET=your-client-secret
```

2. Start the application:
```bash
mvn spring-boot:run
```

3. Access protected endpoint:
```bash
curl http://localhost:8080/api/bookings/availability?roomType=DELUXE
```

4. You will be redirected to Azure AD login page
5. After successful login, you'll receive a JWT token
6. Use the token for subsequent API calls:
```bash
curl -H "Authorization: Bearer <jwt-token>" \
  http://localhost:8080/api/bookings/availability?roomType=DELUXE
```

### Production Testing

1. Navigate to your application URL: `https://your-app.azurewebsites.net`
2. Access any protected endpoint
3. You'll be redirected to Azure AD login
4. Login with your organizational account
5. After successful authentication, you'll be redirected back to the application

## Security Benefits

### Centralized Identity Management
- All users managed in Azure AD
- No need to maintain separate user database
- Consistent identity across all Azure services

### Enhanced Security
- Multi-factor authentication (MFA) support
- Conditional access policies (location, device, risk-based)
- Password policies enforced by Azure AD
- Account lockout and breach detection

### Compliance and Auditing
- Comprehensive authentication logs in Azure AD
- Sign-in activity reports
- Audit trail for all authentication events
- Compliance with enterprise security standards

### Scalability
- Stateless authentication (JWT tokens)
- No session affinity required
- Horizontal scaling without authentication issues
- Works seamlessly with Azure load balancers

### Developer Experience
- Single Sign-On (SSO) across applications
- Standard OAuth 2.0 / OpenID Connect protocols
- Easy integration with other Azure services
- Managed by Azure platform (no credential management)

## Troubleshooting

### Common Issues

**Issue**: "AADSTS50011: The reply URL specified in the request does not match"
- **Solution**: Add the redirect URI to Azure AD app registration

**Issue**: "AADSTS700016: Application not found in the directory"
- **Solution**: Verify AZURE_AD_CLIENT_ID and AZURE_AD_TENANT_ID are correct

**Issue**: "Unauthorized" when accessing endpoints
- **Solution**: Ensure user is member of allowed Azure AD groups

**Issue**: "Invalid client secret"
- **Solution**: Regenerate client secret in Azure AD and update environment variable

### Enable Debug Logging

Add to application.properties:
```properties
logging.level.com.azure.spring.aad=DEBUG
logging.level.org.springframework.security=DEBUG
```

## Migration Notes

### Changes Made for cr-java-0090 Fix

1. **pom.xml**:
   - Added `spring-boot-starter-security` dependency
   - Added `azure-spring-boot-starter-active-directory` dependency

2. **SecurityConfig.java** (NEW):
   - Created Spring Security configuration
   - Integrated Azure AD authentication
   - Configured endpoint security rules
   - Enabled stateless session management

3. **BookingController.java**:
   - Added `@PreAuthorize` annotations to all endpoints
   - Added authentication context to retrieve logged-in user
   - Added role-based access control for admin endpoints

4. **application.properties**:
   - Added Azure AD configuration properties
   - Configured tenant ID, client ID, client secret
   - Configured allowed groups for authorization

5. **BookingService.java**:
   - Added documentation about authentication
   - Service methods now called from authenticated controllers only

### Backward Compatibility

- H2 console remains accessible for development (should be disabled in production)
- Health check endpoint remains public for load balancer health checks
- All business logic remains unchanged
- Only authentication layer was added

## Next Steps

1. **Production Deployment**:
   - Store client secret in Azure Key Vault
   - Configure production redirect URIs
   - Enable MFA for all users
   - Set up conditional access policies

2. **Enhanced Authorization**:
   - Create additional Azure AD groups for fine-grained access control
   - Implement method-level security with `@PreAuthorize`
   - Add custom claims to JWT tokens

3. **Monitoring**:
   - Enable Azure AD sign-in logs
   - Set up alerts for failed authentication attempts
   - Monitor token validation failures

4. **Security Hardening**:
   - Disable H2 console in production
   - Enable HTTPS only
   - Implement rate limiting
   - Add API gateway for additional security layer
