package com.demo.resortslite;

import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AzureKeyVaultConfig {

    @Bean
    public DefaultAzureCredential defaultAzureCredential() {
        return new DefaultAzureCredentialBuilder().build();
    }

    @Bean
    public SecretClient secretClient(
            DefaultAzureCredential credential,
            @Value("${azure.keyvault.url:}") String keyVaultUrl) {
        if (keyVaultUrl == null || keyVaultUrl.trim().isEmpty()) {
            return null;
        }
        return new SecretClientBuilder()
                .vaultUrl(keyVaultUrl)
                .credential(credential)
                .buildClient();
    }
}
