package com.demo.resortslite;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for microservices communication.
 * 
 * Fixed: cz-java-0082 - Individual Components
 * Provides RestTemplate bean for inter-service communication in microservices architecture.
 * This enables the booking service to communicate with external inventory service.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
