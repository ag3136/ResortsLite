package com.demo.resortslite;

import com.azure.core.credential.AzureNamedKeyCredential;
import com.azure.data.appconfiguration.ConfigurationClient;
import com.azure.data.appconfiguration.ConfigurationClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AzureAppConfigurationService {

    private final String connectionString;
    private final String endpoint;
    private final String keyId;
    private final String secret;

    public AzureAppConfigurationService(
            @Value("${azure.appconfig.connection-string:}") String connectionString,
            @Value("${azure.appconfig.endpoint:}") String endpoint,
            @Value("${azure.appconfig.key-id:}") String keyId,
            @Value("${azure.appconfig.secret:}") String secret) {
        this.connectionString = connectionString;
        this.endpoint = endpoint;
        this.keyId = keyId;
        this.secret = secret;
    }

    public String getValue(String key, String defaultValue) {
        try {
            ConfigurationClient client = buildClient();
            if (client == null) {
                return resolveEnvFallback(key, defaultValue);
            }
            String value = client.getConfigurationSetting(key, null).getValue();
            return (value == null || value.trim().isEmpty()) ? resolveEnvFallback(key, defaultValue) : value;
        } catch (Exception ex) {
            return resolveEnvFallback(key, defaultValue);
        }
    }

    private ConfigurationClient buildClient() {
        if (connectionString != null && !connectionString.trim().isEmpty()) {
            return new ConfigurationClientBuilder().connectionString(connectionString).buildClient();
        }
        if (endpoint != null && !endpoint.trim().isEmpty() && keyId != null && !keyId.trim().isEmpty()
                && secret != null && !secret.trim().isEmpty()) {
            return new ConfigurationClientBuilder()
                    .endpoint(endpoint)
                    .credential(new AzureNamedKeyCredential(keyId, secret))
                    .buildClient();
        }
        return null;
    }

    private String resolveEnvFallback(String key, String defaultValue) {
        String envKey = key.toUpperCase().replace('.', '_').replace('-', '_');
        String envValue = System.getenv(envKey);
        return (envValue == null || envValue.trim().isEmpty()) ? defaultValue : envValue;
    }
}
