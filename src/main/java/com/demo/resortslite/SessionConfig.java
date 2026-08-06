package com.demo.resortslite;

import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * Spring Session Configuration for Externalized Session Storage
 * 
 * FIXED cz-java-0069: In-Memory Session Storage
 * 
 * This configuration enables Spring Session with Redis backend, replacing
 * in-memory HttpSession storage with Amazon ElastiCache (Redis) for EKS.
 * 
 * Benefits:
 * - Sessions persist across container restarts
 * - Sessions are shared across all pod replicas (horizontal scaling)
 * - No session affinity (sticky sessions) required at load balancer
 * - Automatic session expiration and cleanup
 * 
 * Redis connection configured via application.properties:
 * - spring.redis.host=${REDIS_HOST:localhost}
 * - spring.redis.port=${REDIS_PORT:6379}
 * - spring.redis.password=${REDIS_PASSWORD:}
 * 
 * For EKS deployment, use IRSA (IAM Roles for Service Accounts) to
 * securely access Amazon ElastiCache without hardcoded credentials.
 */
@Configuration
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 1800) // 30 minutes session timeout
public class SessionConfig {
    // Spring Session automatically intercepts HttpSession calls
    // and stores session data in Redis instead of in-memory
}
