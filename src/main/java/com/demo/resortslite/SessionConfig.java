package com.demo.resortslite;

import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * Spring Session Configuration for Azure Cache for Redis
 * 
 * FIXED cr-java-0065: HTTP Session State Storage
 * 
 * This configuration enables Redis-backed HTTP session storage, replacing in-memory sessions.
 * Benefits:
 * - Stateless application architecture: No server affinity required
 * - Horizontal scaling: Sessions are shared across all application instances
 * - High availability: Azure Cache for Redis provides automatic failover
 * - Session persistence: Sessions survive application restarts and deployments
 * - Load balancing: Any instance can handle any request without sticky sessions
 * 
 * Azure Cache for Redis Configuration:
 * - Connection details are externalized to application.properties
 * - Use environment variables for secure credential management:
 *   - AZURE_REDIS_HOST: Redis server hostname (e.g., myredis.redis.cache.windows.net)
 *   - AZURE_REDIS_PORT: Redis server port (default: 6379 or 6380 for SSL)
 *   - AZURE_REDIS_PASSWORD: Redis access key from Azure Portal
 *   - AZURE_REDIS_SSL: Enable SSL/TLS for secure connections (recommended: true)
 * 
 * Session timeout is configured in application.properties (default: 1800 seconds / 30 minutes)
 */
@Configuration
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 1800)
public class SessionConfig {
    // Spring Session automatically configures Redis-backed session repository
    // No additional bean definitions required when using Spring Boot auto-configuration
}
