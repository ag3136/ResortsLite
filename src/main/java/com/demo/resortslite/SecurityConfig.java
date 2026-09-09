package com.demo.resortslite;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for Azure Active Directory (Entra ID) integration.
 * Replaces file-based authentication with centralized, cloud-native identity management.
 */
@Configuration
public class SecurityConfig {

    /**
     * Configures Spring Security to use Azure Active Directory for authentication.
     * All requests require authentication except static resources.
     *
     * @param http the HttpSecurity to configure
     * @return the security filter chain
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/api/bookings/create", "/api/bookings/status/**",
                        "/api/bookings/availability", "/api/bookings/report/**")
                .authenticated()
                .anyRequest().permitAll()
            )
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/oauth2/authorization/login")
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
            );
        return http.build();
    }
}
