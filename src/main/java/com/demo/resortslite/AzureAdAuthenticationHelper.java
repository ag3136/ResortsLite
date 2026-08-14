package com.demo.resortslite;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Azure Active Directory Authentication Helper
 * 
 * FIXED cr-java-0090: Centralized Azure AD authentication utilities
 * 
 * This helper class provides convenient methods to access Azure AD user information
 * from JWT tokens throughout the application. It replaces file-based authentication
 * patterns with cloud-native Azure AD integration.
 * 
 * Features:
 * - Extract user email, name, and ID from Azure AD JWT tokens
 * - Retrieve user roles and groups from Azure AD
 * - Check user authentication status
 * - Provide user context for audit logging
 * 
 * Usage:
 * - Inject this component into any service that needs user authentication context
 * - Use methods to get current user information from Azure AD
 * - All methods handle unauthenticated scenarios gracefully
 */
@Component
public class AzureAdAuthenticationHelper {

    /**
     * Get the currently authenticated user's email address
     * 
     * @return User email from Azure AD, or "anonymous" if not authenticated
     */
    public String getCurrentUserEmail() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof Jwt) {
                Jwt jwt = (Jwt) authentication.getPrincipal();
                String email = jwt.getClaimAsString("preferred_username");
                if (email == null) {
                    email = jwt.getClaimAsString("email");
                }
                return email != null ? email : "anonymous";
            }
            return "anonymous";
        } catch (Exception e) {
            return "anonymous";
        }
    }

    /**
     * Get the currently authenticated user's unique identifier (OID)
     * 
     * @return User OID from Azure AD, or "anonymous" if not authenticated
     */
    public String getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof Jwt) {
                Jwt jwt = (Jwt) authentication.getPrincipal();
                String oid = jwt.getClaimAsString("oid");
                if (oid == null) {
                    oid = jwt.getClaimAsString("sub");
                }
                return oid != null ? oid : "anonymous";
            }
            return "anonymous";
        } catch (Exception e) {
            return "anonymous";
        }
    }

    /**
     * Get the currently authenticated user's display name
     * 
     * @return User name from Azure AD, or "Anonymous User" if not authenticated
     */
    public String getCurrentUserName() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof Jwt) {
                Jwt jwt = (Jwt) authentication.getPrincipal();
                String name = jwt.getClaimAsString("name");
                return name != null ? name : "Anonymous User";
            }
            return "Anonymous User";
        } catch (Exception e) {
            return "Anonymous User";
        }
    }

    /**
     * Check if the current user is authenticated
     * 
     * @return true if user is authenticated with Azure AD, false otherwise
     */
    public boolean isAuthenticated() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            return authentication != null 
                && authentication.isAuthenticated() 
                && authentication.getPrincipal() instanceof Jwt;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get all user information from Azure AD JWT token
     * 
     * @return Map containing user email, ID, name, and authentication status
     */
    public Map<String, String> getCurrentUserInfo() {
        Map<String, String> userInfo = new HashMap<>();
        userInfo.put("email", getCurrentUserEmail());
        userInfo.put("userId", getCurrentUserId());
        userInfo.put("name", getCurrentUserName());
        userInfo.put("authenticated", String.valueOf(isAuthenticated()));
        return userInfo;
    }

    /**
     * Get the raw JWT token for the current user
     * 
     * @return JWT token, or null if not authenticated
     */
    public Jwt getCurrentUserToken() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof Jwt) {
                return (Jwt) authentication.getPrincipal();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
