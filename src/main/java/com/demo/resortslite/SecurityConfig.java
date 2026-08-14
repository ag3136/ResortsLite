package com.demo.resortslite;

import com.azure.spring.cloud.autoconfigure.aad.AadResourceServerWebSecurityConfigurerAdapter;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

/**
 * Azure Active Directory Security Configuration
 * 
 * FIXED cr-java-0090: Migrated from file-based authentication to Azure AD (Entra ID)
 * 
 * This configuration replaces legacy file-based credential storage with cloud-native
 * Azure Active Directory authentication using OAuth2 and JWT tokens.
 * 
 * Benefits:
 * - Centralized identity management across all Azure services
 * - Single Sign-On (SSO) support
 * - Multi-Factor Authentication (MFA) enforcement
 * - Role-Based Access Control (RBAC) with Azure AD groups
 * - Audit logging and compliance reporting
 * - No credential storage in application code or files
 * - Automatic token refresh and validation
 * - Integration with Azure Key Vault for secrets management
 * 
 * Configuration:
 * - Azure AD tenant ID, client ID, and client secret are externalized to environment variables
 * - JWT tokens are validated against Azure AD's public keys
 * - User authentication is handled by Azure AD OAuth2 flow
 * - API endpoints are protected with JWT bearer token authentication
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig extends AadResourceServerWebSecurityConfigurerAdapter {

    /**
     * Configure HTTP security with Azure AD JWT authentication
     * 
     * Security rules:
     * - H2 console is accessible without authentication (development only)
     * - Actuator endpoints require authentication
     * - All API endpoints require valid Azure AD JWT token
     * - CSRF protection is enabled for state-changing operations
     */
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        super.configure(http);
        
        http
            .authorizeRequests()
                // Allow H2 console access for development (disable in production)
                .antMatchers("/h2-console/**").permitAll()
                // Require authentication for actuator endpoints
                .antMatchers("/actuator/**").authenticated()
                // Require authentication for all API endpoints
                .antMatchers("/api/**").authenticated()
                // All other requests require authentication
                .anyRequest().authenticated()
            .and()
                // Enable OAuth2 Resource Server with JWT token validation
                .oauth2ResourceServer()
                    .jwt();
        
        // Allow H2 console to work in frames (development only)
        http.headers().frameOptions().sameOrigin();
        
        // CSRF protection - disable for H2 console (development only)
        http.csrf().ignoringAntMatchers("/h2-console/**");
    }
}
