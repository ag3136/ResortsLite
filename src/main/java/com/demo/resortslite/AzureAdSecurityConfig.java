package com.demo.resortslite;

import com.azure.spring.cloud.autoconfigure.aad.AadResourceServerWebSecurityConfigurerAdapter;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

/**
 * Azure Active Directory Security Configuration
 * 
 * FIXED: cr-java-0090 - Migrated from file-based authentication to Azure AD
 * 
 * This configuration integrates Azure Active Directory (Entra ID) authentication
 * using Microsoft Authentication Library (MSAL) and Spring Security.
 * 
 * Features:
 * - Centralized identity management via Azure AD
 * - OAuth2 JWT token validation
 * - Role-based access control (RBAC)
 * - Scalable authentication for cloud environments
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class AzureAdSecurityConfig extends AadResourceServerWebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        super.configure(http);
        
        http
            .authorizeRequests()
                // Public endpoints - no authentication required
                .antMatchers("/actuator/health", "/h2-console/**").permitAll()
                // All other endpoints require authentication
                .anyRequest().authenticated()
            .and()
            .csrf()
                // Disable CSRF for H2 console (development only)
                .ignoringAntMatchers("/h2-console/**")
            .and()
            .headers()
                // Allow H2 console to be displayed in frames (development only)
                .frameOptions().sameOrigin();
    }
}
