package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * AWS Cognito Configuration
 * FIXED [cr-java-0090]: Migrated from file-based authentication to AWS Cognito
 * 
 * This configuration replaces local file-based user authentication with AWS Cognito,
 * providing centralized, encrypted, and auditable authentication with built-in user
 * lifecycle management. AWS Secrets Manager is used for storing sensitive credentials,
 * while AWS Cognito handles user identity and authentication.
 * 
 * Benefits:
 * - Centralized user identity management
 * - Built-in security features (MFA, password policies, account recovery)
 * - Scalable authentication across distributed cloud environments
 * - Integration with AWS IAM for fine-grained access control
 * - Audit logging through AWS CloudTrail
 */
@Configuration
public class AwsCognitoConfig {

    @Value("${aws.cognito.user-pool-id:}")
    private String userPoolId;

    @Value("${aws.cognito.client-id:}")
    private String clientId;

    @Value("${aws.cognito.region:us-east-1}")
    private String awsRegion;

    private CognitoIdentityProviderClient cognitoClient;

    @PostConstruct
    public void init() {
        // Initialize AWS Cognito Identity Provider client
        cognitoClient = CognitoIdentityProviderClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /**
     * Authenticate user with AWS Cognito
     * Replaces file-based authentication with cloud-native identity management
     * 
     * @param username User's username
     * @param password User's password
     * @return Authentication result containing access token, ID token, and refresh token
     * @throws Exception if authentication fails
     */
    public Map<String, String> authenticateUser(String username, String password) throws Exception {
        Map<String, String> authParameters = new HashMap<>();
        authParameters.put("USERNAME", username);
        authParameters.put("PASSWORD", password);

        InitiateAuthRequest authRequest = InitiateAuthRequest.builder()
                .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
                .clientId(clientId)
                .authParameters(authParameters)
                .build();

        try {
            InitiateAuthResponse authResponse = cognitoClient.initiateAuth(authRequest);
            AuthenticationResultType authResult = authResponse.authenticationResult();

            Map<String, String> tokens = new HashMap<>();
            tokens.put("accessToken", authResult.accessToken());
            tokens.put("idToken", authResult.idToken());
            tokens.put("refreshToken", authResult.refreshToken());
            tokens.put("tokenType", authResult.tokenType());
            tokens.put("expiresIn", String.valueOf(authResult.expiresIn()));

            return tokens;
        } catch (NotAuthorizedException e) {
            throw new Exception("Invalid username or password", e);
        } catch (UserNotFoundException e) {
            throw new Exception("User not found", e);
        } catch (Exception e) {
            throw new Exception("Authentication failed: " + e.getMessage(), e);
        }
    }

    /**
     * Verify access token with AWS Cognito
     * 
     * @param accessToken JWT access token from Cognito
     * @return User attributes if token is valid
     * @throws Exception if token is invalid or expired
     */
    public Map<String, String> verifyToken(String accessToken) throws Exception {
        GetUserRequest getUserRequest = GetUserRequest.builder()
                .accessToken(accessToken)
                .build();

        try {
            GetUserResponse getUserResponse = cognitoClient.getUser(getUserRequest);
            
            Map<String, String> userAttributes = new HashMap<>();
            userAttributes.put("username", getUserResponse.username());
            
            // Extract user attributes
            for (AttributeType attribute : getUserResponse.userAttributes()) {
                userAttributes.put(attribute.name(), attribute.value());
            }
            
            return userAttributes;
        } catch (NotAuthorizedException e) {
            throw new Exception("Invalid or expired token", e);
        } catch (Exception e) {
            throw new Exception("Token verification failed: " + e.getMessage(), e);
        }
    }

    /**
     * Create new user in AWS Cognito User Pool
     * 
     * @param username User's username
     * @param password User's password
     * @param email User's email address
     * @param attributes Additional user attributes
     * @return User sub (unique identifier)
     * @throws Exception if user creation fails
     */
    public String createUser(String username, String password, String email, Map<String, String> attributes) throws Exception {
        AdminCreateUserRequest.Builder requestBuilder = AdminCreateUserRequest.builder()
                .userPoolId(userPoolId)
                .username(username)
                .temporaryPassword(password)
                .messageAction(MessageActionType.SUPPRESS); // Don't send welcome email

        // Add email attribute
        AttributeType emailAttribute = AttributeType.builder()
                .name("email")
                .value(email)
                .build();

        requestBuilder.userAttributes(emailAttribute);

        // Add additional attributes if provided
        if (attributes != null && !attributes.isEmpty()) {
            for (Map.Entry<String, String> entry : attributes.entrySet()) {
                AttributeType attr = AttributeType.builder()
                        .name(entry.getKey())
                        .value(entry.getValue())
                        .build();
                requestBuilder.userAttributes(attr);
            }
        }

        try {
            AdminCreateUserResponse response = cognitoClient.adminCreateUser(requestBuilder.build());
            return response.user().username();
        } catch (UsernameExistsException e) {
            throw new Exception("Username already exists", e);
        } catch (Exception e) {
            throw new Exception("User creation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Delete user from AWS Cognito User Pool
     * 
     * @param username User's username
     * @throws Exception if user deletion fails
     */
    public void deleteUser(String username) throws Exception {
        AdminDeleteUserRequest deleteRequest = AdminDeleteUserRequest.builder()
                .userPoolId(userPoolId)
                .username(username)
                .build();

        try {
            cognitoClient.adminDeleteUser(deleteRequest);
        } catch (UserNotFoundException e) {
            throw new Exception("User not found", e);
        } catch (Exception e) {
            throw new Exception("User deletion failed: " + e.getMessage(), e);
        }
    }

    /**
     * Update user attributes in AWS Cognito
     * 
     * @param username User's username
     * @param attributes Attributes to update
     * @throws Exception if update fails
     */
    public void updateUserAttributes(String username, Map<String, String> attributes) throws Exception {
        AdminUpdateUserAttributesRequest.Builder requestBuilder = AdminUpdateUserAttributesRequest.builder()
                .userPoolId(userPoolId)
                .username(username);

        // Convert attributes map to AttributeType list
        if (attributes != null && !attributes.isEmpty()) {
            for (Map.Entry<String, String> entry : attributes.entrySet()) {
                AttributeType attr = AttributeType.builder()
                        .name(entry.getKey())
                        .value(entry.getValue())
                        .build();
                requestBuilder.userAttributes(attr);
            }
        }

        try {
            cognitoClient.adminUpdateUserAttributes(requestBuilder.build());
        } catch (UserNotFoundException e) {
            throw new Exception("User not found", e);
        } catch (Exception e) {
            throw new Exception("User attribute update failed: " + e.getMessage(), e);
        }
    }

    /**
     * Get user details from AWS Cognito
     * 
     * @param username User's username
     * @return User attributes
     * @throws Exception if user retrieval fails
     */
    public Map<String, String> getUserDetails(String username) throws Exception {
        AdminGetUserRequest getUserRequest = AdminGetUserRequest.builder()
                .userPoolId(userPoolId)
                .username(username)
                .build();

        try {
            AdminGetUserResponse getUserResponse = cognitoClient.adminGetUser(getUserRequest);
            
            Map<String, String> userDetails = new HashMap<>();
            userDetails.put("username", getUserResponse.username());
            userDetails.put("userStatus", getUserResponse.userStatusAsString());
            userDetails.put("enabled", String.valueOf(getUserResponse.enabled()));
            userDetails.put("userCreateDate", getUserResponse.userCreateDate().toString());
            userDetails.put("userLastModifiedDate", getUserResponse.userLastModifiedDate().toString());
            
            // Extract user attributes
            for (AttributeType attribute : getUserResponse.userAttributes()) {
                userDetails.put(attribute.name(), attribute.value());
            }
            
            return userDetails;
        } catch (UserNotFoundException e) {
            throw new Exception("User not found", e);
        } catch (Exception e) {
            throw new Exception("Failed to retrieve user details: " + e.getMessage(), e);
        }
    }

    /**
     * Change user password in AWS Cognito
     * 
     * @param accessToken User's current access token
     * @param oldPassword Current password
     * @param newPassword New password
     * @throws Exception if password change fails
     */
    public void changePassword(String accessToken, String oldPassword, String newPassword) throws Exception {
        ChangePasswordRequest changePasswordRequest = ChangePasswordRequest.builder()
                .accessToken(accessToken)
                .previousPassword(oldPassword)
                .proposedPassword(newPassword)
                .build();

        try {
            cognitoClient.changePassword(changePasswordRequest);
        } catch (NotAuthorizedException e) {
            throw new Exception("Invalid current password", e);
        } catch (InvalidPasswordException e) {
            throw new Exception("New password does not meet password policy requirements", e);
        } catch (Exception e) {
            throw new Exception("Password change failed: " + e.getMessage(), e);
        }
    }

    /**
     * Sign out user from AWS Cognito
     * 
     * @param accessToken User's access token
     * @throws Exception if sign out fails
     */
    public void signOut(String accessToken) throws Exception {
        GlobalSignOutRequest signOutRequest = GlobalSignOutRequest.builder()
                .accessToken(accessToken)
                .build();

        try {
            cognitoClient.globalSignOut(signOutRequest);
        } catch (Exception e) {
            throw new Exception("Sign out failed: " + e.getMessage(), e);
        }
    }

    @Bean
    public CognitoIdentityProviderClient cognitoIdentityProviderClient() {
        return cognitoClient;
    }

    public String getUserPoolId() {
        return userPoolId;
    }

    public String getClientId() {
        return clientId;
    }
}
