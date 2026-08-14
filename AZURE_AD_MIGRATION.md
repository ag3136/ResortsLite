# Azure Active Directory Authentication Migration

## Overview

**FIXED: cr-java-0090 - File-based Authentication**

This application has been migrated from file-based authentication to Azure Active Directory (Entra ID) authentication using Spring Security and Microsoft Authentication Library (MSAL).

## What Changed

### Before (File-based Authentication)
- Credentials stored in local files or hardcoded in source code
- MD5 hashing for authentication tokens (insecure)
- No centralized identity management
- Manual user management and password resets
- No audit trail or compliance reporting
- Security vulnerabilities with credential storage

### After (Azure AD Authentication)
- Centralized identity management via Azure Active Directory
- OAuth2 and JWT token-based authentication
- Single Sign-On (SSO) support
- Multi-Factor Authentication (MFA) enforcement
- Role-Based Access Control (RBAC) with Azure AD groups
- Complete audit trail and compliance reporting
- No credentials stored in application code or files
- Automatic token refresh and validation

## Architecture

### Components Added

1. **SecurityConfig.java**
   - Spring Security configuration for Azure AD integration
   - JWT token validation against Azure AD public keys
   - OAuth2 Resource Server configuration
   - Endpoint security rules

2. **AzureAdAuthenticationHelper.java**
   - Utility class for accessing Azure AD user information
   - Extract user email, name, and ID from JWT tokens
   - Check authentication status
   - Provide user context for audit logging

3. **Updated BookingService.java**
   - Replaced MD5 hashing with SHA-256
   - Added Azure AD user context to booking records
   - Audit trail with authenticated user information
   - Secure confirmation code generation

4. **Updated BookingController.java**
   - Added @PreAuthorize annotations for endpoint security
   - Inject Azure AD user context into API responses
   - Authentication required for booking operations

### Dependencies Added

```xml
<!-- Azure Active Directory Spring Boot Starter -->
<dependency>
    <groupId>com.azure.spring</groupId>
    <artifactId>azure-spring-boot-starter-active-directory</artifactId>
    <version>3.14.0</version>
</dependency>

<!-- Spring Security -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Spring Security OAuth2 Resource Server -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>

<!-- Spring Security OAuth2 Client -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>
```

## Configuration

### Required Environment Variables

Set these environment variables for Azure AD authentication:

```bash
# Azure AD Configuration
AZURE_AD_ENABLED=true
AZURE_AD_TENANT_ID=<your-tenant-id>
AZURE_AD_CLIENT_ID=<your-client-id>
AZURE_AD_CLIENT_SECRET=<your-client-secret>
AZURE_AD_APP_ID_URI=<your-app-id-uri>

# Security Settings
SECURITY_ENABLED=true
```

### Azure AD Setup Steps

1. **Register Application in Azure AD**
   - Go to Azure Portal → Azure Active Directory → App registrations
   - Click "New registration"
   - Name: "ResortsLite API"
   - Supported account types: Single tenant
   - Click "Register"

2. **Configure API Permissions**
   - Go to API permissions
   - Add permission → Microsoft Graph → Delegated permissions
   - Select: User.Read
   - Grant admin consent

3. **Create Client Secret**
   - Go to Certificates & secrets
   - Click "New client secret"
   - Description: "ResortsLite API Secret"
   - Expires: 24 months (or as per policy)
   - Copy the secret value (AZURE_AD_CLIENT_SECRET)

4. **Configure App ID URI**
   - Go to Expose an API
   - Set Application ID URI: api://resortslite
   - Add a scope: api://resortslite/Booking.ReadWrite

5. **Note Configuration Values**
   - Tenant ID: Found in Overview page
   - Client ID (Application ID): Found in Overview page
   - Client Secret: Copied in step 3

### Production Configuration

For production deployments, store secrets in Azure Key Vault:

```bash
# Create Key Vault
az keyvault create --name resortslite-kv --resource-group resortslite-rg --location eastus

# Store secrets
az keyvault secret set --vault-name resortslite-kv --name "AZURE-AD-TENANT-ID" --value "<tenant-id>"
az keyvault secret set --vault-name resortslite-kv --name "AZURE-AD-CLIENT-ID" --value "<client-id>"
az keyvault secret set --vault-name resortslite-kv --name "AZURE-AD-CLIENT-SECRET" --value "<client-secret>"

# Grant application access to Key Vault
az keyvault set-policy --name resortslite-kv --object-id <app-object-id> --secret-permissions get list
```

## API Authentication

### Obtaining Access Token

To call the API, clients must obtain an access token from Azure AD:

```bash
# Get access token using client credentials flow
curl -X POST https://login.microsoftonline.com/<tenant-id>/oauth2/v2.0/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=<client-id>" \
  -d "client_secret=<client-secret>" \
  -d "scope=api://resortslite/.default" \
  -d "grant_type=client_credentials"
```

### Calling Protected Endpoints

Include the access token in the Authorization header:

```bash
# Create booking with Azure AD authentication
curl -X POST http://localhost:8080/api/bookings/create \
  -H "Authorization: Bearer <access-token>" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "guestName=John Doe" \
  -d "roomType=DELUXE" \
  -d "checkIn=2024-03-01" \
  -d "checkOut=2024-03-05"
```

## Security Benefits

### 1. Centralized Identity Management
- All users managed in Azure AD
- Single source of truth for user identities
- Consistent authentication across all Azure services

### 2. Enhanced Security
- No credentials stored in application code or files
- JWT tokens with short expiration times
- Automatic token refresh
- MFA enforcement at Azure AD level

### 3. Audit and Compliance
- Complete audit trail of authentication events
- User activity logging in Azure AD
- Compliance reporting for SOC 2, ISO 27001, etc.
- Integration with Azure Monitor and Log Analytics

### 4. Role-Based Access Control
- Define roles and permissions in Azure AD
- Map Azure AD groups to application roles
- Fine-grained access control
- Centralized permission management

### 5. Scalability
- No local authentication state
- Stateless JWT token validation
- Horizontal scaling without session affinity
- Cloud-native authentication pattern

## Testing

### Local Development

For local development, you can disable Azure AD authentication:

```properties
# application-local.properties
AZURE_AD_ENABLED=false
SECURITY_ENABLED=false
```

### Integration Testing

Use Azure AD test users or service principals for integration testing:

```java
@SpringBootTest
@AutoConfigureMockMvc
public class BookingControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    @WithMockUser(username = "test@example.com")
    public void testCreateBooking() throws Exception {
        mockMvc.perform(post("/api/bookings/create")
                .param("guestName", "John Doe")
                .param("roomType", "DELUXE")
                .param("checkIn", "2024-03-01")
                .param("checkOut", "2024-03-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("confirmed"));
    }
}
```

## Migration Checklist

- [x] Add Azure AD Spring Boot Starter dependencies
- [x] Configure Spring Security with Azure AD
- [x] Create SecurityConfig for JWT validation
- [x] Create AzureAdAuthenticationHelper utility
- [x] Update BookingService to use Azure AD user context
- [x] Update BookingController with @PreAuthorize annotations
- [x] Replace MD5 hashing with SHA-256
- [x] Add Azure AD configuration to application.properties
- [x] Document Azure AD setup and configuration
- [ ] Register application in Azure AD portal
- [ ] Configure API permissions and scopes
- [ ] Create client secret and store in Key Vault
- [ ] Update deployment configuration with Azure AD settings
- [ ] Test authentication flow end-to-end
- [ ] Update API documentation with authentication requirements

## Troubleshooting

### Common Issues

1. **401 Unauthorized**
   - Check that access token is valid and not expired
   - Verify token is included in Authorization header
   - Ensure Azure AD configuration is correct

2. **403 Forbidden**
   - Check user has required permissions
   - Verify Azure AD group membership
   - Review @PreAuthorize annotations

3. **Token Validation Failed**
   - Verify AZURE_AD_TENANT_ID is correct
   - Check JWT issuer URI configuration
   - Ensure clock synchronization

### Debug Logging

Enable debug logging for Spring Security:

```properties
logging.level.org.springframework.security=DEBUG
logging.level.com.azure.spring=DEBUG
```

## References

- [Azure Active Directory Documentation](https://docs.microsoft.com/en-us/azure/active-directory/)
- [Spring Security Azure AD Integration](https://docs.microsoft.com/en-us/azure/developer/java/spring-framework/spring-boot-starter-for-azure-active-directory-developer-guide)
- [OAuth 2.0 and OpenID Connect](https://docs.microsoft.com/en-us/azure/active-directory/develop/v2-protocols)
- [Azure Key Vault for Secrets Management](https://docs.microsoft.com/en-us/azure/key-vault/)
