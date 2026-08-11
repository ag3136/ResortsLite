package com.demo.resortslite.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * Redis Session Configuration for Amazon ElastiCache
 * 
 * Enables distributed session management using Spring Session with Redis/ElastiCache.
 * This configuration eliminates server affinity and enables horizontal scaling by
 * storing all HTTP session data in a centralized Redis cluster.
 * 
 * Benefits:
 * - Stateless application instances (no session data stored locally)
 * - Seamless load balancing across multiple EC2 instances
 * - Session persistence during auto-scaling events
 * - High availability with ElastiCache replication
 * 
 * Configuration:
 * - Redis host/port configured via environment variables (REDIS_HOST, REDIS_PORT)
 * - Session timeout: 30 minutes (1800 seconds)
 * - Session namespace: spring:session
 */
@Configuration
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 1800)
public class RedisSessionConfig {

    /**
     * Creates Redis connection factory for Spring Session
     * Connection details are injected from application.properties which reads
     * from environment variables (REDIS_HOST, REDIS_PORT)
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory();
    }
}
