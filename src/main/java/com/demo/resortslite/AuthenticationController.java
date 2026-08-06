package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Authentication Controller
 * FIXED [cr-java-0090]: Provides REST endpoints for AWS Cognito authentication
 * 
 * This controller replaces file-based authentication with cloud-native AWS Cognito
 * identity management, providing secure, scalable authentication for cloud deployments.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private AwsCognitoConfig awsCognitoConfig;

    /**
     * Authenticate user with username and password
     * 
     * @param credentials Map containing username and password
     * @return Authentication tokens if successful
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> credentials) {
        String username = credentials.get("username");
        String password = credentials.get("password");

        if (username == null || password == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Username and password are required");
            return ResponseEntity.badRequest().body(error);
        }

        Map<String, Object> result = bookingService.authenticateUser(username, password);
        
        if (Boolean.TRUE.equals(result.get("success"))) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
        }
    }

    /**
     * Verify access token
     * 
     * @param token Map containing access token
     * @return User information if token is valid
     */
    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyToken(@RequestBody Map<String, String> token) {
        String accessToken = token.get("accessToken");

        if (accessToken == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("valid", false);
            error.put("message", "Access token is required");
            return ResponseEntity.badRequest().body(error);
        }

        Map<String, Object> result = bookingService.verifyUserToken(accessToken);
        
        if (Boolean.TRUE.equals(result.get("valid"))) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
        }
    }

    /**
     * Get user details
     * 
     * @param username Username to retrieve
     * @return User details from Cognito
     */
    @GetMapping("/user/{username}")
    public ResponseEntity<Map<String, Object>> getUserDetails(@PathVariable String username) {
        Map<String, Object> result = bookingService.getUserDetails(username);
        
        if (Boolean.TRUE.equals(result.get("success"))) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
        }
    }

    /**
     * Create new user
     * 
     * @param userRequest Map containing user details
     * @return Created user information
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> registerUser(@RequestBody Map<String, String> userRequest) {
        String username = userRequest.get("username");
        String password = userRequest.get("password");
        String email = userRequest.get("email");

        if (username == null || password == null || email == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Username, password, and email are required");
            return ResponseEntity.badRequest().body(error);
        }

        Map<String, Object> result = new HashMap<>();
        try {
            // Extract additional attributes
            Map<String, String> attributes = new HashMap<>();
            for (Map.Entry<String, String> entry : userRequest.entrySet()) {
                if (!entry.getKey().equals("username") && 
                    !entry.getKey().equals("password") && 
                    !entry.getKey().equals("email")) {
                    attributes.put(entry.getKey(), entry.getValue());
                }
            }

            String createdUsername = awsCognitoConfig.createUser(username, password, email, attributes);
            
            result.put("success", true);
            result.put("username", createdUsername);
            result.put("message", "User created successfully");
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("message", "User registration failed");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        }
    }

    /**
     * Change user password
     * 
     * @param passwordRequest Map containing access token, old password, and new password
     * @return Success or error message
     */
    @PostMapping("/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(@RequestBody Map<String, String> passwordRequest) {
        String accessToken = passwordRequest.get("accessToken");
        String oldPassword = passwordRequest.get("oldPassword");
        String newPassword = passwordRequest.get("newPassword");

        if (accessToken == null || oldPassword == null || newPassword == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Access token, old password, and new password are required");
            return ResponseEntity.badRequest().body(error);
        }

        Map<String, Object> result = new HashMap<>();
        try {
            awsCognitoConfig.changePassword(accessToken, oldPassword, newPassword);
            
            result.put("success", true);
            result.put("message", "Password changed successfully");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("message", "Password change failed");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        }
    }

    /**
     * Sign out user
     * 
     * @param token Map containing access token
     * @return Success or error message
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(@RequestBody Map<String, String> token) {
        String accessToken = token.get("accessToken");

        if (accessToken == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Access token is required");
            return ResponseEntity.badRequest().body(error);
        }

        Map<String, Object> result = new HashMap<>();
        try {
            awsCognitoConfig.signOut(accessToken);
            
            result.put("success", true);
            result.put("message", "Signed out successfully");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("message", "Sign out failed");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        }
    }

    /**
     * Health check endpoint
     * 
     * @return Cognito configuration status
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "AWS Cognito Authentication");
        health.put("userPoolId", awsCognitoConfig.getUserPoolId());
        health.put("clientId", awsCognitoConfig.getClientId());
        health.put("configured", 
            awsCognitoConfig.getUserPoolId() != null && 
            !awsCognitoConfig.getUserPoolId().isEmpty());
        return ResponseEntity.ok(health);
    }
}
