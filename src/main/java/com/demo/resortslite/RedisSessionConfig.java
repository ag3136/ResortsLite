package com.demo.resortslite;

import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * Redis Session Configuration for Amazon ElastiCache
 * 
 * FIXED cr-java-0065: Enables distributed session management using Amazon ElastiCache for Redis.
 * 
 * This configuration:
 * - Replaces in-memory HTTP session storage with Redis-backed distributed sessions
 * - Enables stateless application instances that can scale horizontally
 * - Allows AWS ALB to distribute requests across any EC2 instance without session affinity
 * - Provides session persistence across instance restarts, deployments, and auto-scaling events
 * - Supports multi-AZ deployment with ElastiCache replication for high availability
 * 
 * Session data is automatically serialized to Redis and shared across all application instances.
 * Spring Session transparently intercepts HttpSession API calls and persists to ElastiCache.
 * 
 * Configuration properties in application.properties:
 * - spring.redis.host: ElastiCache cluster endpoint (externalized via AWS Parameter Store)
 * - spring.redis.port: Redis port (default 6379)
 * - spring.redis.password: ElastiCache auth token (externalized via AWS Secrets Manager)
 * - spring.session.store-type: redis
 * 
 * @maxInactiveIntervalInSeconds: Session timeout in seconds (default 1800 = 30 minutes)
 */
@Configuration
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 1800)
public class RedisSessionConfig {
    // Spring Boot auto-configuration handles Redis connection setup
    // based on spring.redis.* properties in application.properties
}
