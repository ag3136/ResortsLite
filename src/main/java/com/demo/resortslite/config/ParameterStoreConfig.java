package com.demo.resortslite.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.SsmException;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * Configuration class for AWS Systems Manager Parameter Store integration.
 * Provides centralized access to externalized configuration parameters stored in AWS SSM.
 * 
 * FIXED cr-java-0071: Replaces hard-coded environment URLs with Parameter Store retrieval.
 */
@Configuration
public class ParameterStoreConfig {

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    private SsmClient ssmClient;

    @PostConstruct
    public void initializeSsmClient() {
        // Initialize SSM client with default credentials provider
        // In AWS cloud environment, this will use IAM role credentials automatically
        ssmClient = SsmClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @PreDestroy
    public void closeSsmClient() {
        if (ssmClient != null) {
            ssmClient.close();
        }
    }

    @Bean
    public SsmClient ssmClient() {
        return ssmClient;
    }

    /**
     * Retrieves a parameter value from AWS Systems Manager Parameter Store.
     * 
     * @param parameterName The name/path of the parameter in Parameter Store
     * @return The parameter value
     * @throws RuntimeException if parameter retrieval fails
     */
    public String getParameter(String parameterName) {
        try {
            GetParameterRequest request = GetParameterRequest.builder()
                    .name(parameterName)
                    .withDecryption(true) // Decrypt SecureString parameters
                    .build();

            GetParameterResponse response = ssmClient.getParameter(request);
            return response.parameter().value();
        } catch (SsmException e) {
            throw new RuntimeException("Failed to retrieve parameter: " + parameterName + 
                    " - " + e.awsErrorDetails().errorMessage(), e);
        }
    }

    /**
     * Retrieves a parameter value with a fallback default value.
     * 
     * @param parameterName The name/path of the parameter in Parameter Store
     * @param defaultValue The default value to return if parameter is not found
     * @return The parameter value or default value
     */
    public String getParameterOrDefault(String parameterName, String defaultValue) {
        try {
            return getParameter(parameterName);
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
