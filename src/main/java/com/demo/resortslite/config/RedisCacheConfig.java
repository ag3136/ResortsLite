package com.demo.resortslite.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * FIXED cr-java-0067: Redis Cache Configuration for Amazon ElastiCache
 * 
 * Replaces unbounded in-memory caching with Amazon ElastiCache for Redis.
 * Implements proper TTL (Time-To-Live) policies to ensure:
 * - Controlled cache expiration (30 minutes default)
 * - Consistent data across multiple application instances
 * - Centralized cache management
 * - Prevention of indefinite memory growth
 * - Elimination of stale data inconsistencies
 * 
 * Configuration:
 * - Cache TTL: 30 minutes (configurable via application.properties)
 * - Serialization: JSON format for cross-platform compatibility
 * - Connection: Uses shared RedisConnectionFactory from RedisSessionConfig
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

    @Value("${spring.cache.redis.time-to-live:1800000}")
    private long cacheTtlMillis;

    /**
     * Configures Redis-based cache manager with TTL policies
     * Connects to Amazon ElastiCache for Redis cluster
     * 
     * @param redisConnectionFactory Shared Redis connection factory
     * @return Configured cache manager with TTL and serialization settings
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        RedisCacheConfiguration cacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMillis(cacheTtlMillis))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
                )
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer())
                )
                .disableCachingNullValues();

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(cacheConfig)
                .transactionAware()
                .build();
    }
}
