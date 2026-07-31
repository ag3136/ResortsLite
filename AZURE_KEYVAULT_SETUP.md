# Azure Key Vault Setup Guide

## Overview

This application has been migrated to use Azure Key Vault for secure credential management, addressing the hard-coded database credentials security vulnerability (cr-java-0069).

## What Was Fixed

**Before:**
- Database credentials (DB_USER, DB_PASS) were hard-coded in `BookingService.java` (lines 22-23)
- Credentials were exposed in source code and version control
- No credential rotation capability

**After:**
- Credentials are stored securely in Azure Key Vault
- Application retrieves credentials at runtime using Azure SDK
- Uses `DefaultAzureCredential` for secure authentication
- Supports Managed Identity for Azure-hosted applications

## Prerequisites

1. **Azure Key Vault**: Create an Azure Key Vault instance
2. **Managed Identity**: Enable Managed Identity on your Azure App Service or Container Instance
3. **Access Policy**: Grant the Managed Identity "Get" and "List" permissions on secrets

## Azure Key Vault Setup

### Step 1: Create Key Vault

```bash
# Create resource group (if not exists)
az group create --name myResourceGroup --location eastus

# Create Key Vault
az keyvault create \
  --name myResortsKeyVault \
  --resource-group myResourceGroup \
  --location eastus
```

### Step 2: Add Secrets

```bash
# Add database host secret
az keyvault secret set \
  --vault-name myResortsKeyVault \
  --name db-host \
  --value "db-prod.resorts-internal.com"

# Add database user secret
az keyvault secret set \
  --vault-name myResortsKeyVault \
  --name db-user \
  --value "admin"

# Add database password secret
az keyvault secret set \
  --vault-name myResortsKeyVault \
  --name db-password \
  --value "YourSecurePassword"
```

### Step 3: Configure Managed Identity

```bash
# Enable system-assigned managed identity on App Service
az webapp identity assign \
  --name myResortsApp \
  --resource-group myResourceGroup

# Get the principal ID (will be displayed in output)
PRINCIPAL_ID=$(az webapp identity show \
  --name myResortsApp \
  --resource-group myResourceGroup \
  --query principalId -o tsv)

# Grant access to Key Vault
az keyvault set-policy \
  --name myResortsKeyVault \
  --object-id $PRINCIPAL_ID \
  --secret-permissions get list
```

## Application Configuration

### Environment Variables

Set the following environment variable in your Azure App Service or Container Instance:

```bash
AZURE_KEYVAULT_URI=https://myResortsKeyVault.vault.azure.net/
```

Optional: Customize secret names (defaults shown):
```bash
AZURE_KEYVAULT_DB_HOST_SECRET=db-host
AZURE_KEYVAULT_DB_USER_SECRET=db-user
AZURE_KEYVAULT_DB_PASS_SECRET=db-password
```

### Local Development

For local development, use Azure CLI authentication:

1. Install Azure CLI: https://docs.microsoft.com/en-us/cli/azure/install-azure-cli
2. Login: `az login`
3. Set environment variable:
   ```bash
   export AZURE_KEYVAULT_URI=https://myResortsKeyVault.vault.azure.net/
   ```

Alternatively, use service principal credentials:
```bash
export AZURE_CLIENT_ID=<your-client-id>
export AZURE_CLIENT_SECRET=<your-client-secret>
export AZURE_TENANT_ID=<your-tenant-id>
export AZURE_KEYVAULT_URI=https://myResortsKeyVault.vault.azure.net/
```

## Authentication Methods

The application uses `DefaultAzureCredential`, which tries authentication methods in this order:

1. **Environment Variables** (AZURE_CLIENT_ID, AZURE_CLIENT_SECRET, AZURE_TENANT_ID)
2. **Managed Identity** (recommended for Azure-hosted apps)
3. **Azure CLI** (for local development)
4. **Azure PowerShell**
5. **Interactive Browser** (fallback)

## Code Changes

### New Files
- `AzureKeyVaultConfig.java`: Configuration class for SecretClient bean

### Modified Files
- `BookingService.java`: Removed hard-coded credentials, added Key Vault integration
- `pom.xml`: Added Azure Key Vault and Azure Identity dependencies
- `application.properties`: Added Key Vault configuration properties

### Dependencies Added
```xml
<dependency>
    <groupId>com.azure</groupId>
    <artifactId>azure-security-keyvault-secrets</artifactId>
    <version>4.6.0</version>
</dependency>
<dependency>
    <groupId>com.azure</groupId>
    <artifactId>azure-identity</artifactId>
    <version>1.11.0</version>
</dependency>
```

## Security Benefits

1. **No Credentials in Code**: Credentials are never stored in source code or version control
2. **Centralized Management**: All secrets managed in one secure location
3. **Credential Rotation**: Secrets can be rotated without code changes or redeployment
4. **Audit Trail**: Azure Key Vault logs all secret access
5. **Access Control**: Fine-grained access control using Azure RBAC
6. **Encryption**: Secrets encrypted at rest and in transit

## Troubleshooting

### Error: "Secret not found"
- Verify the secret name matches the configuration
- Check that the Managed Identity has "Get" permission on secrets

### Error: "Authentication failed"
- For Managed Identity: Ensure it's enabled and has Key Vault access
- For local development: Run `az login` and verify you have access to the Key Vault

### Error: "Key Vault URI is empty"
- Set the `AZURE_KEYVAULT_URI` environment variable
- For local development without Key Vault, the app will fall back to environment variables

## Migration Checklist

- [x] Remove hard-coded credentials from source code
- [x] Add Azure Key Vault dependencies to pom.xml
- [x] Create AzureKeyVaultConfig configuration class
- [x] Update BookingService to use SecretClient
- [x] Add Key Vault configuration to application.properties
- [ ] Create Azure Key Vault instance
- [ ] Add secrets to Key Vault
- [ ] Enable Managed Identity on Azure App Service
- [ ] Grant Key Vault access to Managed Identity
- [ ] Set AZURE_KEYVAULT_URI environment variable
- [ ] Test application in Azure environment
- [ ] Remove old environment variables (DB_USER, DB_PASS)

## References

- [Azure Key Vault Documentation](https://docs.microsoft.com/en-us/azure/key-vault/)
- [Azure SDK for Java](https://docs.microsoft.com/en-us/azure/developer/java/sdk/)
- [DefaultAzureCredential](https://docs.microsoft.com/en-us/java/api/com.azure.identity.defaultazurecredential)
- [Managed Identity](https://docs.microsoft.com/en-us/azure/active-directory/managed-identities-azure-resources/)
