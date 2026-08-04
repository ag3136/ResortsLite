package com.demo.resortslite.config;

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
 * Redis Cache Configuration for Google Cloud Memorystore
 * 
 * FIXED cr-java-0067: Migrated from unbounded in-memory HashMap cache to 
 * Google Cloud Memorystore for Redis with proper TTL configuration.
 * 
 * Benefits:
 * - Shared cache state across all application instances (horizontal scaling)
 * - Automatic TTL expiration prevents memory exhaustion
 * - Consistent cache behavior in distributed cloud environment
 * - No cache synchronization issues across multiple instances
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

    /**
     * Configure Redis-backed cache manager with TTL
     * 
     * Cache entries automatically expire after 1 hour (3600 seconds)
     * This prevents indefinite memory growth and ensures fresh data
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1)) // TTL: 1 hour - prevents unbounded cache growth
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues(); // Don't cache null values

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .transactionAware() // Enable transaction support
                .build();
    }
}
