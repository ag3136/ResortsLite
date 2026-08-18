package com.demo.resortslite.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration class for AWS Secrets Manager integration.
 * Retrieves database credentials and other sensitive configuration from AWS Secrets Manager
 * instead of hard-coding them in source code or property files.
 */
@Configuration
public class SecretsManagerConfig {

    @Value("${aws.secretsmanager.secret.name:resorts-db-credentials}")
    private String secretName;

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    private Map<String, String> secrets = new HashMap<>();

    @Bean
    public SecretsManagerClient secretsManagerClient() {
        return SecretsManagerClient.builder()
                .region(Region.of(awsRegion))
                .build();
    }

    @PostConstruct
    public void loadSecrets() {
        try {
            SecretsManagerClient client = secretsManagerClient();
            GetSecretValueRequest getSecretValueRequest = GetSecretValueRequest.builder()
                    .secretId(secretName)
                    .build();

            GetSecretValueResponse getSecretValueResponse = client.getSecretValue(getSecretValueRequest);
            String secretString = getSecretValueResponse.secretString();

            // Parse JSON secret
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode secretJson = objectMapper.readTree(secretString);

            // Extract credentials from JSON
            if (secretJson.has("DB_HOST")) {
                secrets.put("DB_HOST", secretJson.get("DB_HOST").asText());
            }
            if (secretJson.has("DB_USER")) {
                secrets.put("DB_USER", secretJson.get("DB_USER").asText());
            }
            if (secretJson.has("DB_PASS")) {
                secrets.put("DB_PASS", secretJson.get("DB_PASS").asText());
            }

            System.out.println("Successfully loaded secrets from AWS Secrets Manager: " + secretName);
        } catch (Exception e) {
            System.err.println("Failed to load secrets from AWS Secrets Manager: " + e.getMessage());
            // Fallback to environment variables if Secrets Manager is not available
            secrets.put("DB_HOST", System.getenv().getOrDefault("DB_HOST", "localhost"));
            secrets.put("DB_USER", System.getenv().getOrDefault("DB_USER", "sa"));
            secrets.put("DB_PASS", System.getenv().getOrDefault("DB_PASS", ""));
        }
    }

    public String getSecret(String key) {
        return secrets.getOrDefault(key, "");
    }

    public String getDbHost() {
        return getSecret("DB_HOST");
    }

    public String getDbUser() {
        return getSecret("DB_USER");
    }

    public String getDbPassword() {
        return getSecret("DB_PASS");
    }
}
