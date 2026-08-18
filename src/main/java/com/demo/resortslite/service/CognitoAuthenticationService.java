package com.demo.resortslite.service;

import com.demo.resortslite.config.CognitoAuthenticationConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Authentication service using Amazon Cognito for user identity management.
 * 
 * This service replaces file-based authentication with cloud-native identity management:
 * - User credentials are stored securely in Amazon Cognito User Pool
 * - Authentication tokens (JWT) are issued by Cognito
 * - No local file storage of credentials or user data
 * - Supports horizontal scaling and distributed deployments
 * - Provides built-in security features (password policies, MFA, account recovery)
 * 
 * FIXED: cr-java-0090 - File-based Authentication
 * Previously: Authentication credentials stored in local files
 * Now: Centralized authentication using AWS Cognito with AWS Secrets Manager for credentials
 */
@Service
public class CognitoAuthenticationService {

    @Autowired
    private CognitoIdentityProviderClient cognitoClient;

    @Autowired
    private CognitoAuthenticationConfig cognitoConfig;

    /**
     * Authenticates a user against Amazon Cognito User Pool.
     * 
     * @param username User's username or email
     * @param password User's password
     * @return Authentication result containing access token, ID token, and refresh token
     * @throws AuthenticationException if authentication fails
     */
    public Map<String, String> authenticateUser(String username, String password) {
        Map<String, String> result = new HashMap<>();
        
        try {
            // Prepare authentication parameters
            Map<String, String> authParams = new HashMap<>();
            authParams.put("USERNAME", username);
            authParams.put("PASSWORD", password);

            // Initiate authentication with Cognito
            InitiateAuthRequest authRequest = InitiateAuthRequest.builder()
                    .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
                    .clientId(cognitoConfig.getClientId())
                    .authParameters(authParams)
                    .build();

            InitiateAuthResponse authResponse = cognitoClient.initiateAuth(authRequest);

            // Extract authentication tokens
            AuthenticationResultType authResult = authResponse.authenticationResult();
            if (authResult != null) {
                result.put("accessToken", authResult.accessToken());
                result.put("idToken", authResult.idToken());
                result.put("refreshToken", authResult.refreshToken());
                result.put("tokenType", authResult.tokenType());
                result.put("expiresIn", String.valueOf(authResult.expiresIn()));
                result.put("status", "success");
            } else {
                result.put("status", "challenge_required");
                result.put("challengeName", authResponse.challengeNameAsString());
            }

        } catch (NotAuthorizedException e) {
            result.put("status", "failed");
            result.put("error", "Invalid username or password");
        } catch (UserNotFoundException e) {
            result.put("status", "failed");
            result.put("error", "User not found");
        } catch (Exception e) {
            result.put("status", "failed");
            result.put("error", "Authentication failed: " + e.getMessage());
        }

        return result;
    }

    /**
     * Validates an access token issued by Amazon Cognito.
     * 
     * @param accessToken JWT access token to validate
     * @return User information if token is valid
     */
    public Map<String, Object> validateToken(String accessToken) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            GetUserRequest getUserRequest = GetUserRequest.builder()
                    .accessToken(accessToken)
                    .build();

            GetUserResponse getUserResponse = cognitoClient.getUser(getUserRequest);

            result.put("username", getUserResponse.username());
            result.put("userAttributes", getUserResponse.userAttributes());
            result.put("valid", true);

        } catch (NotAuthorizedException e) {
            result.put("valid", false);
            result.put("error", "Invalid or expired token");
        } catch (Exception e) {
            result.put("valid", false);
            result.put("error", "Token validation failed: " + e.getMessage());
        }

        return result;
    }

    /**
     * Registers a new user in Amazon Cognito User Pool.
     * 
     * @param username User's username
     * @param password User's password
     * @param email User's email address
     * @return Registration result
     */
    public Map<String, String> registerUser(String username, String password, String email) {
        Map<String, String> result = new HashMap<>();
        
        try {
            AttributeType emailAttribute = AttributeType.builder()
                    .name("email")
                    .value(email)
                    .build();

            SignUpRequest signUpRequest = SignUpRequest.builder()
                    .clientId(cognitoConfig.getClientId())
                    .username(username)
                    .password(password)
                    .userAttributes(emailAttribute)
                    .build();

            SignUpResponse signUpResponse = cognitoClient.signUp(signUpRequest);

            result.put("status", "success");
            result.put("userId", signUpResponse.userSub());
            result.put("userConfirmed", String.valueOf(signUpResponse.userConfirmed()));

        } catch (UsernameExistsException e) {
            result.put("status", "failed");
            result.put("error", "Username already exists");
        } catch (InvalidPasswordException e) {
            result.put("status", "failed");
            result.put("error", "Password does not meet requirements");
        } catch (Exception e) {
            result.put("status", "failed");
            result.put("error", "Registration failed: " + e.getMessage());
        }

        return result;
    }

    /**
     * Signs out a user by invalidating their access token.
     * 
     * @param accessToken User's access token
     * @return Sign out result
     */
    public Map<String, String> signOutUser(String accessToken) {
        Map<String, String> result = new HashMap<>();
        
        try {
            GlobalSignOutRequest signOutRequest = GlobalSignOutRequest.builder()
                    .accessToken(accessToken)
                    .build();

            cognitoClient.globalSignOut(signOutRequest);

            result.put("status", "success");
            result.put("message", "User signed out successfully");

        } catch (Exception e) {
            result.put("status", "failed");
            result.put("error", "Sign out failed: " + e.getMessage());
        }

        return result;
    }
}
