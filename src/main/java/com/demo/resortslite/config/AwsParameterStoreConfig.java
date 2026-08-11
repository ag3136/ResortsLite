package com.demo.resortslite.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;

/**
 * AWS Systems Manager Parameter Store configuration for externalized environment-specific URLs.
 * This replaces hard-coded URLs with cloud-native configuration management.
 */
@Configuration
public class AwsParameterStoreConfig {

    private final SsmClient ssmClient;

    public AwsParameterStoreConfig() {
        // Initialize AWS Systems Manager client with default credentials and region
        String region = System.getenv().getOrDefault("AWS_REGION", "us-east-1");
        this.ssmClient = SsmClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    public SsmClient ssmClient() {
        return this.ssmClient;
    }

    /**
     * Retrieves a parameter value from AWS Systems Manager Parameter Store.
     * 
     * @param parameterName The name of the parameter to retrieve
     * @param defaultValue The default value to return if parameter is not found
     * @return The parameter value or default value
     */
    public String getParameter(String parameterName, String defaultValue) {
        try {
            GetParameterRequest request = GetParameterRequest.builder()
                    .name(parameterName)
                    .withDecryption(true)
                    .build();
            
            GetParameterResponse response = ssmClient.getParameter(request);
            return response.parameter().value();
        } catch (Exception e) {
            // Log warning and return default value if parameter not found
            System.err.println("Warning: Could not retrieve parameter '" + parameterName + 
                             "' from AWS Parameter Store. Using default value. Error: " + e.getMessage());
            return defaultValue;
        }
    }
}
