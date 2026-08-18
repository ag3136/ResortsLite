package com.demo.resortslite.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * FIXED cr-java-0065: Redis Session Configuration for Amazon ElastiCache
 * 
 * Enables distributed session management using Spring Session Data Redis.
 * Session data is stored in Amazon ElastiCache for Redis, enabling:
 * - Stateless application instances
 * - Horizontal scaling across multiple EC2 instances
 * - Session persistence during auto-scaling events
 * - Load balancing without session affinity
 * 
 * Configuration:
 * - REDIS_HOST: ElastiCache cluster endpoint (e.g., my-cluster.abc123.0001.use1.cache.amazonaws.com)
 * - REDIS_PORT: Redis port (default: 6379)
 * - Session timeout: 30 minutes (1800 seconds)
 */
@Configuration
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 1800)
public class RedisSessionConfig {

    @Value("${spring.redis.host}")
    private String redisHost;

    @Value("${spring.redis.port}")
    private int redisPort;

    /**
     * Creates Redis connection factory for Amazon ElastiCache
     * Uses Lettuce client for non-blocking, reactive Redis connections
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
        redisConfig.setHostName(redisHost);
        redisConfig.setPort(redisPort);
        
        return new LettuceConnectionFactory(redisConfig);
    }
}
