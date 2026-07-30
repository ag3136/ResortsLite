# Azure Key Vault Setup Guide

## Overview
This application has been migrated to use Azure Key Vault for secure credential management, replacing hard-coded database credentials with cloud-native secret management.

## Prerequisites
- Azure subscription
- Azure CLI installed and configured
- Appropriate permissions to create and manage Azure Key Vault resources

## Setup Instructions

### 1. Create Azure Key Vault

```bash
# Set variables
RESOURCE_GROUP="resorts-lite-rg"
KEYVAULT_NAME="resorts-lite-kv"
LOCATION="eastus"

# Create resource group (if not exists)
az group create --name $RESOURCE_GROUP --location $LOCATION

# Create Key Vault
az keyvault create \
  --name $KEYVAULT_NAME \
  --resource-group $RESOURCE_GROUP \
  --location $LOCATION
```

### 2. Store Database Credentials in Key Vault

```bash
# Store database username
az keyvault secret set \
  --vault-name $KEYVAULT_NAME \
  --name "db-username" \
  --value "your-database-username"

# Store database password
az keyvault secret set \
  --vault-name $KEYVAULT_NAME \
  --name "db-password" \
  --value "your-database-password"
```

### 3. Configure Managed Identity (for Azure App Service / Container Apps)

```bash
# Enable system-assigned managed identity for your App Service
az webapp identity assign \
  --name your-app-name \
  --resource-group $RESOURCE_GROUP

# Get the principal ID
PRINCIPAL_ID=$(az webapp identity show \
  --name your-app-name \
  --resource-group $RESOURCE_GROUP \
  --query principalId -o tsv)

# Grant the managed identity access to Key Vault
az keyvault set-policy \
  --name $KEYVAULT_NAME \
  --object-id $PRINCIPAL_ID \
  --secret-permissions get list
```

### 4. Configure Application Environment Variables

Set the following environment variables in your Azure App Service or Container App:

```bash
AZURE_KEYVAULT_URL=https://resorts-lite-kv.vault.azure.net/
AZURE_KEYVAULT_DB_USERNAME_SECRET=db-username
AZURE_KEYVAULT_DB_PASSWORD_SECRET=db-password
```

For Azure App Service:
```bash
az webapp config appsettings set \
  --name your-app-name \
  --resource-group $RESOURCE_GROUP \
  --settings \
    AZURE_KEYVAULT_URL=https://$KEYVAULT_NAME.vault.azure.net/ \
    AZURE_KEYVAULT_DB_USERNAME_SECRET=db-username \
    AZURE_KEYVAULT_DB_PASSWORD_SECRET=db-password
```

## Local Development

For local development, you can use one of the following authentication methods:

### Option 1: Azure CLI Authentication
```bash
# Login to Azure CLI
az login

# Set environment variable
export AZURE_KEYVAULT_URL=https://resorts-lite-kv.vault.azure.net/
```

### Option 2: Environment Variables (Fallback)
```bash
# Set database credentials as environment variables
export DB_USER=your-local-db-username
export DB_PASS=your-local-db-password
```

## Authentication Methods Supported

The application uses `DefaultAzureCredential` which supports multiple authentication methods in the following order:

1. **Environment Variables** - `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_CLIENT_SECRET`
2. **Managed Identity** - For Azure App Service, Container Apps, VMs
3. **Azure CLI** - For local development
4. **Azure PowerShell** - Alternative for local development
5. **Interactive Browser** - Fallback for local development

## Security Best Practices

1. **Never commit credentials** to source control
2. **Use Managed Identity** in production environments
3. **Rotate secrets regularly** using Azure Key Vault
4. **Enable Key Vault logging** for audit trails
5. **Use RBAC** for fine-grained access control
6. **Enable soft-delete** and purge protection on Key Vault

## Troubleshooting

### Issue: "Failed to retrieve secrets from Azure Key Vault"
- Verify the Key Vault URL is correct
- Ensure the managed identity has proper permissions
- Check that the secret names match the configuration

### Issue: "Authentication failed"
- For local development, ensure you're logged in via Azure CLI: `az login`
- For production, verify the managed identity is enabled and has Key Vault access

### Issue: "Secret not found"
- Verify the secret names in Key Vault match the configuration
- Check that secrets are not in a deleted state (soft-delete enabled)

## Migration Checklist

- [x] Added Azure Key Vault dependencies to pom.xml
- [x] Removed hard-coded credentials from BookingService.java
- [x] Implemented Azure Key Vault integration with DefaultAzureCredential
- [x] Added configuration properties for Key Vault
- [x] Implemented fallback mechanism for local development
- [x] Added error handling for Key Vault connection failures

## References

- [Azure Key Vault Documentation](https://docs.microsoft.com/azure/key-vault/)
- [DefaultAzureCredential Documentation](https://docs.microsoft.com/java/api/com.azure.identity.defaultazurecredential)
- [Azure SDK for Java](https://docs.microsoft.com/azure/developer/java/sdk/)
