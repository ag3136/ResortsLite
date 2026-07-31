# Azure Active Directory Authentication Setup

## Overview
This application has been migrated from file-based authentication to Azure Active Directory (Entra ID) for centralized, cloud-native identity management.

## Fixed Issue: cr-java-0090 - File-based Authentication

### What Was Changed
- **Removed**: MD5-based local authentication and confirmation code generation
- **Added**: Azure AD OAuth2 authentication with Spring Security
- **Added**: Secure confirmation code generation using authenticated user context
- **Added**: Spring Security configuration for Azure AD integration

### Benefits
- ✅ Centralized identity management via Azure Active Directory
- ✅ Secure OAuth2 JWT token-based authentication
- ✅ Scalable authentication for distributed cloud environments
- ✅ Role-based access control (RBAC) support
- ✅ Audit trail with authenticated user tracking
- ✅ No local credential storage

## Azure AD Setup Instructions

### 1. Register Application in Azure AD

1. Navigate to [Azure Portal](https://portal.azure.com)
2. Go to **Azure Active Directory** > **App registrations**
3. Click **New registration**
4. Configure:
   - **Name**: ResortsLite
   - **Supported account types**: Accounts in this organizational directory only
   - **Redirect URI**: Web - `https://your-app-url/login/oauth2/code/azure`
5. Click **Register**

### 2. Configure Application

1. Note the **Application (client) ID** and **Directory (tenant) ID**
2. Go to **Certificates & secrets**
3. Create a new **Client secret**
4. Note the secret value (store securely in Azure Key Vault)

### 3. Configure API Permissions

1. Go to **API permissions**
2. Add permissions:
   - Microsoft Graph > Delegated > User.Read
3. Grant admin consent

### 4. Set Environment Variables

Set the following environment variables in your Azure Container App or AKS deployment:

```bash
# Azure AD Configuration
export AZURE_AD_ENABLED=true
export AZURE_TENANT_ID=<your-tenant-id>
export AZURE_CLIENT_ID=<your-client-id>
export AZURE_CLIENT_SECRET=<your-client-secret>  # Store in Key Vault

# Optional: App ID URI for custom scopes
export AZURE_APP_ID_URI=api://<your-client-id>
```

### 5. Store Secrets in Azure Key Vault (Recommended)

Instead of environment variables, store sensitive values in Azure Key Vault:

```bash
# Store client secret in Key Vault
az keyvault secret set --vault-name <your-keyvault> --name azure-ad-client-secret --value <client-secret>

# Reference in application
export AZURE_CLIENT_SECRET=${@Microsoft.KeyVault(SecretUri=https://<your-keyvault>.vault.azure.net/secrets/azure-ad-client-secret/)}
```

## Authentication Flow

### 1. User Authentication
- User accesses protected endpoint
- Spring Security redirects to Azure AD login
- User authenticates with Azure AD credentials
- Azure AD issues JWT access token
- Token is validated by Spring Security

### 2. Booking Creation with Authentication
- Authenticated user creates booking
- System generates secure confirmation code using:
  - Booking ID
  - Guest name
  - Authenticated user from Azure AD (audit trail)
- Confirmation code uses SHA-256 (secure) instead of MD5

### 3. API Access
- All API requests require valid Azure AD JWT token
- Token must be included in Authorization header:
  ```
  Authorization: Bearer <jwt-token>
  ```

## Testing Authentication

### Local Development
For local testing without Azure AD:
```properties
spring.cloud.azure.active-directory.enabled=false
```

### With Azure AD
1. Obtain access token from Azure AD
2. Include token in API requests:
   ```bash
   curl -H "Authorization: Bearer <token>" https://your-app/api/bookings
   ```

## Security Endpoints

- **Public**: `/actuator/health`, `/h2-console/**` (development only)
- **Protected**: All other endpoints require Azure AD authentication

## Role-Based Access Control (Optional)

To implement RBAC:

1. Define app roles in Azure AD app registration manifest
2. Assign roles to users/groups in Azure AD
3. Use `@PreAuthorize` annotations in code:
   ```java
   @PreAuthorize("hasAuthority('APPROLE_Admin')")
   public void adminOnlyMethod() { ... }
   ```

## Troubleshooting

### Common Issues

1. **401 Unauthorized**
   - Verify Azure AD configuration
   - Check token expiration
   - Ensure correct tenant ID and client ID

2. **403 Forbidden**
   - Check user has required roles/permissions
   - Verify API permissions granted in Azure AD

3. **Token Validation Failed**
   - Verify issuer URL matches tenant
   - Check token audience matches client ID
   - Ensure clock synchronization

## Migration Notes

### Before (File-based Authentication)
- Used MD5 hashing for confirmation codes (insecure)
- No centralized identity management
- No audit trail
- Not scalable for cloud environments

### After (Azure AD Authentication)
- OAuth2 JWT token-based authentication
- Centralized identity via Azure AD
- Authenticated user tracking for audit
- Scalable for distributed cloud deployments
- SHA-256 for secure hashing

## References

- [Azure AD Spring Boot Starter](https://github.com/Azure/azure-sdk-for-java/tree/main/sdk/spring/spring-cloud-azure-starter-active-directory)
- [Spring Security OAuth2 Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html)
- [Azure AD Authentication](https://learn.microsoft.com/en-us/azure/active-directory/develop/)
