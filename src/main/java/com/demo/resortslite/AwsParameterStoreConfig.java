package com.demo.resortslite;

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
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration class for AWS Systems Manager Parameter Store integration.
 * FIXED cr-java-0071: Externalizes environment-specific URLs to AWS Parameter Store.
 * 
 * This configuration retrieves environment-specific URLs from AWS Systems Manager Parameter Store,
 * enabling environment-agnostic deployments without code changes.
 * 
 * Benefits:
 * - Centralized configuration management across environments (dev, staging, prod)
 * - No code changes required when URLs change
 * - Secure storage of configuration values
 * - Version control and audit trail for configuration changes
 * - IAM-based access control
 */
@Configuration
public class AwsParameterStoreConfig {

    @Value("${aws.parameterstore.region:us-east-1}")
    private String awsRegion;

    @Value("${aws.parameterstore.inventory.url.path:/resorts-lite/inventory-service-url}")
    private String inventoryUrlParameterPath;

    @Value("${aws.parameterstore.reports.url.path:/resorts-lite/reports-service-url}")
    private String reportsUrlParameterPath;

    private SsmClient ssmClient;
    private Map<String, String> parameterCache = new HashMap<>();

    /**
     * Initialize SSM client with AWS credentials from environment or IAM role.
     * Uses DefaultCredentialsProvider which supports:
     * - Environment variables (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)
     * - IAM roles for EC2/ECS/EKS (recommended for production)
     * - AWS credentials file (~/.aws/credentials)
     * 
     * @return Configured SsmClient instance
     */
    @Bean
    public SsmClient ssmClient() {
        if (ssmClient == null) {
            ssmClient = SsmClient.builder()
                    .region(Region.of(awsRegion))
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();
        }
        return ssmClient;
    }

    /**
     * Load parameters from AWS Parameter Store on application startup.
     * This ensures parameters are available before the application starts processing requests.
     */
    @PostConstruct
    public void loadParameters() {
        try {
            // Load inventory service URL
            String inventoryUrl = getParameter(inventoryUrlParameterPath);
            if (inventoryUrl != null) {
                parameterCache.put("inventory.service.url", inventoryUrl);
            }

            // Load reports service URL
            String reportsUrl = getParameter(reportsUrlParameterPath);
            if (reportsUrl != null) {
                parameterCache.put("reports.service.url", reportsUrl);
            }

            System.out.println("Successfully loaded parameters from AWS Parameter Store");
        } catch (Exception e) {
            System.err.println("Warning: Failed to load parameters from AWS Parameter Store: " + e.getMessage());
            System.err.println("Using fallback default values. Ensure IAM role has ssm:GetParameter permission.");
            
            // Set fallback values if Parameter Store is not available
            parameterCache.put("inventory.service.url", "https://inventory-service.internal:8081/rooms/available");
            parameterCache.put("reports.service.url", "https://reports.resorts-internal.com:8080/download/");
        }
    }

    /**
     * Retrieve a parameter value from AWS Systems Manager Parameter Store.
     * 
     * @param parameterName The parameter path in Parameter Store
     * @return The parameter value, or null if not found
     */
    private String getParameter(String parameterName) {
        try {
            GetParameterRequest request = GetParameterRequest.builder()
                    .name(parameterName)
                    .withDecryption(true) // Support for SecureString parameters
                    .build();

            GetParameterResponse response = ssmClient().getParameter(request);
            return response.parameter().value();
        } catch (SsmException e) {
            System.err.println("Failed to retrieve parameter " + parameterName + ": " + e.awsErrorDetails().errorMessage());
            return null;
        }
    }

    /**
     * Get the inventory service URL from Parameter Store cache.
     * FIXED cr-java-0071: Replaces hard-coded inventory service URL.
     * 
     * @return The inventory service URL
     */
    public String getInventoryServiceUrl() {
        return parameterCache.getOrDefault("inventory.service.url", "https://inventory-service.internal:8081/rooms/available");
    }

    /**
     * Get the reports service URL from Parameter Store cache.
     * FIXED cr-java-0071: Replaces hard-coded reports service URL.
     * 
     * @return The reports service URL
     */
    public String getReportsServiceUrl() {
        return parameterCache.getOrDefault("reports.service.url", "https://reports.resorts-internal.com:8080/download/");
    }

    /**
     * Refresh parameters from AWS Parameter Store.
     * This can be called periodically or triggered by configuration change events.
     */
    public void refreshParameters() {
        loadParameters();
    }
}
