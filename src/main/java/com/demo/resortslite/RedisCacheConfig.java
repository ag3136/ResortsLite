package com.demo.resortslite;

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
 * FIXED cr-java-0067: Redis Cache Configuration for Azure Cache for Redis
 * 
 * This configuration replaces in-memory caching with distributed Redis caching
 * to enable horizontal scaling and prevent memory exhaustion in cloud environments.
 * 
 * Benefits:
 * - Distributed cache shared across all application instances
 * - TTL policies prevent indefinite memory growth
 * - Cache consistency across scaled instances
 * - Automatic eviction of stale data
 * - Azure Cache for Redis provides high availability and persistence
 * 
 * Configuration:
 * - Default TTL: 1 hour (3600 seconds)
 * - JSON serialization for complex objects
 * - String serialization for cache keys
 * - Configurable via application.properties
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

    /**
     * Configure Redis-based cache manager with TTL policies
     * 
     * @param connectionFactory Redis connection factory (auto-configured by Spring Boot)
     * @return CacheManager configured for Azure Cache for Redis
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Configure Redis cache with TTL and serialization
        RedisCacheConfiguration cacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                // Set default TTL to 1 hour (prevents indefinite memory growth)
                .entryTtl(Duration.ofHours(1))
                // Serialize cache keys as strings
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new StringRedisSerializer()))
                // Serialize cache values as JSON (supports complex objects)
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer()))
                // Disable caching of null values
                .disableCachingNullValues();

        // Build and return Redis cache manager
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(cacheConfig)
                .transactionAware()
                .build();
    }
}
