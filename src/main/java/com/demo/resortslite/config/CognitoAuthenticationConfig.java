package com.demo.resortslite.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

/**
 * Configuration class for AWS Cognito integration.
 * Replaces file-based authentication with cloud-native identity management using Amazon Cognito.
 * 
 * Amazon Cognito provides:
 * - Centralized user identity management
 * - Secure authentication and authorization
 * - Built-in user lifecycle management (registration, password reset, MFA)
 * - OAuth 2.0 and OpenID Connect support
 * - Integration with AWS IAM for fine-grained access control
 * 
 * This eliminates the need for file-based credential storage and provides
 * a scalable, secure authentication solution for cloud environments.
 */
@Configuration
public class CognitoAuthenticationConfig {

    @Value("${aws.cognito.userPoolId:}")
    private String userPoolId;

    @Value("${aws.cognito.clientId:}")
    private String clientId;

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    /**
     * Creates AWS Cognito Identity Provider client for user authentication operations.
     * This client is used to:
     * - Authenticate users against Cognito User Pool
     * - Validate JWT tokens
     * - Manage user sessions
     * - Handle user registration and password management
     * 
     * @return CognitoIdentityProviderClient configured for the specified region
     */
    @Bean
    public CognitoIdentityProviderClient cognitoIdentityProviderClient() {
        return CognitoIdentityProviderClient.builder()
                .region(Region.of(awsRegion))
                .build();
    }

    public String getUserPoolId() {
        return userPoolId;
    }

    public String getClientId() {
        return clientId;
    }

    public String getAwsRegion() {
        return awsRegion;
    }
}
