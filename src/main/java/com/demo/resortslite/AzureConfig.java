package com.demo.resortslite;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Azure services configuration for Key Vault and Blob Storage integration.
 * Provides cloud-native secret management and persistent file storage.
 */
@Configuration
public class AzureConfig {

    @Value("${azure.keyvault.uri}")
    private String keyVaultUri;

    @Value("${azure.storage.blob-endpoint}")
    private String blobEndpoint;

    /**
     * Creates Azure Key Vault SecretClient for secure credential management.
     * Uses DefaultAzureCredential for authentication (supports managed identity, service principal, etc.)
     * 
     * @return SecretClient configured for Azure Key Vault
     */
    @Bean
    public SecretClient secretClient() {
        if (keyVaultUri == null || keyVaultUri.isEmpty()) {
            return null;
        }
        
        return new SecretClientBuilder()
                .vaultUrl(keyVaultUri)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
    }

    /**
     * Creates Azure Blob Storage BlobServiceClient for persistent file storage.
     * Uses DefaultAzureCredential for authentication (supports managed identity, service principal, etc.)
     * 
     * @return BlobServiceClient configured for Azure Blob Storage
     */
    @Bean
    public BlobServiceClient blobServiceClient() {
        if (blobEndpoint == null || blobEndpoint.isEmpty()) {
            return null;
        }
        
        return new BlobServiceClientBuilder()
                .endpoint(blobEndpoint)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
    }
}
