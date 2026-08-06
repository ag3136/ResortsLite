package com.demo.resortslite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * AWS Secrets Manager Configuration
 * Retrieves database credentials from AWS Secrets Manager instead of hard-coding them.
 * This enables secure credential management with automatic rotation support.
 */
@Configuration
public class AwsSecretsManagerConfig {

    @Value("${aws.secretsmanager.secret-name:resorts-lite/db-credentials}")
    private String secretName;

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    private SecretsManagerClient secretsManagerClient;
    private Map<String, String> dbCredentials;

    @PostConstruct
    public void init() {
        // Initialize AWS Secrets Manager client
        secretsManagerClient = SecretsManagerClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        // Load credentials from Secrets Manager
        loadCredentials();
    }

    /**
     * Retrieves database credentials from AWS Secrets Manager
     */
    private void loadCredentials() {
        try {
            GetSecretValueRequest getSecretValueRequest = GetSecretValueRequest.builder()
                    .secretId(secretName)
                    .build();

            GetSecretValueResponse getSecretValueResponse = secretsManagerClient.getSecretValue(getSecretValueRequest);
            String secret = getSecretValueResponse.secretString();

            // Parse JSON secret
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode secretJson = objectMapper.readTree(secret);

            dbCredentials = new HashMap<>();
            dbCredentials.put("host", secretJson.has("host") ? secretJson.get("host").asText() : "localhost");
            dbCredentials.put("username", secretJson.has("username") ? secretJson.get("username").asText() : "");
            dbCredentials.put("password", secretJson.has("password") ? secretJson.get("password").asText() : "");
            dbCredentials.put("database", secretJson.has("database") ? secretJson.get("database").asText() : "resortdb");
            dbCredentials.put("port", secretJson.has("port") ? secretJson.get("port").asText() : "3306");

        } catch (Exception e) {
            // Fallback to environment variables if Secrets Manager is not available
            // This allows local development without AWS credentials
            System.err.println("Warning: Unable to retrieve credentials from AWS Secrets Manager: " + e.getMessage());
            System.err.println("Falling back to environment variables or default configuration");
            
            dbCredentials = new HashMap<>();
            dbCredentials.put("host", System.getenv().getOrDefault("DB_HOST", "localhost"));
            dbCredentials.put("username", System.getenv().getOrDefault("DB_USER", "sa"));
            dbCredentials.put("password", System.getenv().getOrDefault("DB_PASS", ""));
            dbCredentials.put("database", System.getenv().getOrDefault("DB_NAME", "resortdb"));
            dbCredentials.put("port", System.getenv().getOrDefault("DB_PORT", "3306"));
        }
    }

    /**
     * Get database host from Secrets Manager
     */
    public String getDbHost() {
        return dbCredentials.get("host");
    }

    /**
     * Get database username from Secrets Manager
     */
    public String getDbUsername() {
        return dbCredentials.get("username");
    }

    /**
     * Get database password from Secrets Manager
     */
    public String getDbPassword() {
        return dbCredentials.get("password");
    }

    /**
     * Get database name from Secrets Manager
     */
    public String getDbName() {
        return dbCredentials.get("database");
    }

    /**
     * Get database port from Secrets Manager
     */
    public String getDbPort() {
        return dbCredentials.get("port");
    }

    /**
     * Refresh credentials from Secrets Manager
     * This can be called periodically to support credential rotation
     */
    public void refreshCredentials() {
        loadCredentials();
    }

    @Bean
    public AwsSecretsManagerConfig awsSecretsManagerConfig() {
        return this;
    }
}
