package com.demo.resortslite;

import com.azure.spring.aad.webapi.AADResourceServerWebSecurityConfigurerAdapter;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;

/**
 * Azure Active Directory Security Configuration
 * 
 * FIXED cr-java-0090: File-based Authentication
 * 
 * This configuration replaces file-based credential storage with Azure Active Directory (Entra ID)
 * authentication using Microsoft Authentication Library (MSAL) and Spring Security Azure AD integration.
 * 
 * Benefits:
 * - Centralized identity management: All users managed in Azure AD
 * - Multi-factor authentication (MFA): Enhanced security with Azure AD MFA
 * - Single Sign-On (SSO): Users authenticate once across all Azure services
 * - Role-based access control (RBAC): Fine-grained permissions via Azure AD groups
 * - Conditional access policies: Control access based on location, device, risk level
 * - Audit logging: Comprehensive authentication logs in Azure AD
 * - No credential storage: Application never stores or manages passwords
 * - Token-based authentication: OAuth 2.0 and OpenID Connect standards
 * - Horizontal scaling: Stateless authentication works across all instances
 * 
 * Azure AD Configuration Required:
 * - Register application in Azure AD (Azure Portal > App Registrations)
 * - Configure redirect URIs for your application
 * - Create app roles or use Azure AD groups for authorization
 * - Set environment variables:
 *   - AZURE_AD_TENANT_ID: Your Azure AD tenant ID
 *   - AZURE_AD_CLIENT_ID: Application (client) ID from app registration
 *   - AZURE_AD_CLIENT_SECRET: Client secret from app registration (store in Key Vault)
 *   - AZURE_AD_APP_ID_URI: Application ID URI (e.g., api://your-app-id)
 * 
 * Authentication Flow:
 * 1. User accesses protected endpoint
 * 2. Application redirects to Azure AD login page
 * 3. User authenticates with Azure AD credentials (username/password + MFA)
 * 4. Azure AD issues JWT access token
 * 5. Application validates token and grants access
 * 6. Token is used for subsequent requests (stateless)
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig extends AADResourceServerWebSecurityConfigurerAdapter {

    /**
     * Configure HTTP security with Azure AD authentication
     * 
     * Security rules:
     * - /actuator/health: Public endpoint for health checks (required for Azure load balancers)
     * - /h2-console/**: Public endpoint for H2 database console (development only)
     * - All other endpoints: Require authentication via Azure AD
     * 
     * Session management:
     * - STATELESS: No server-side sessions, authentication via JWT tokens
     * - Enables horizontal scaling without session affinity
     * - Compatible with Azure Container Apps, App Service, AKS
     */
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        super.configure(http);
        
        http
            .authorizeRequests()
                // Public endpoints - no authentication required
                .antMatchers("/actuator/health").permitAll()
                .antMatchers("/h2-console/**").permitAll()
                // All other endpoints require authentication
                .anyRequest().authenticated()
            .and()
            .sessionManagement()
                // Stateless session management for cloud-native architecture
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .csrf()
                // Disable CSRF for stateless API (tokens provide CSRF protection)
                .disable()
            .headers()
                // Allow H2 console to be displayed in iframe (development only)
                .frameOptions().disable();
    }
}
