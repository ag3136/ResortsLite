package com.demo.resortslite;

import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * Redis Session Configuration for Azure Cache for Redis
 * 
 * FIXED cr-java-0065: HTTP Session State Storage
 * 
 * This configuration enables Spring Session Data Redis to externalize HTTP session state
 * to Azure Cache for Redis, replacing in-memory session storage.
 * 
 * Benefits:
 * - Stateless application architecture - no server affinity required
 * - Horizontal scaling - sessions shared across all application instances
 * - High availability - session data persists even if an instance fails
 * - Auto-scaling friendly - new instances can immediately serve existing sessions
 * 
 * Configuration properties in application.properties:
 * - spring.redis.host: Azure Cache for Redis hostname
 * - spring.redis.port: Redis port (default 6379, 6380 for SSL)
 * - spring.redis.password: Redis access key from Azure Portal
 * - spring.redis.ssl: Enable SSL/TLS for secure connection
 * - spring.session.store-type: Set to 'redis' to use Redis-backed sessions
 * 
 * Session timeout: 30 minutes (1800 seconds)
 * Sessions are automatically expired by Redis TTL mechanism
 */
@Configuration
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 1800)
public class RedisSessionConfig {
    // Spring Boot auto-configuration will create RedisConnectionFactory
    // based on spring.redis.* properties in application.properties
    
    // No additional beans required - Spring Session handles everything automatically
    // HttpSession interface remains unchanged in controllers
    // Session data is transparently stored in Redis instead of in-memory
}
