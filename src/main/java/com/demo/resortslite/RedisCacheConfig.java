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
 * Redis Cache Configuration for Azure Cache for Redis
 * 
 * FIXED cr-java-0067: In-Memory Caching Without TTL
 * 
 * This configuration replaces in-memory caching mechanisms with Azure Cache for Redis
 * with TTL (Time-To-Live) policies to enable distributed caching, prevent memory exhaustion,
 * and ensure cache consistency across multiple instances.
 * 
 * Benefits:
 * - Distributed caching - cache is shared across all application instances
 * - TTL policies - automatic expiration prevents stale data and memory exhaustion
 * - Horizontal scaling - no cache synchronization issues across instances
 * - High availability - cache data persists even if an instance fails
 * - Cloud-native - fully compatible with Azure Container Apps, AKS, and App Service
 * 
 * Cache Configuration:
 * - Default TTL: 30 minutes (1800 seconds)
 * - Booking cache TTL: 1 hour (3600 seconds)
 * - Serialization: JSON format for cross-platform compatibility
 * - Key prefix: Namespace isolation for multi-tenant scenarios
 * 
 * Usage in controllers:
 * - @Cacheable: Cache method results
 * - @CachePut: Update cache entries
 * - @CacheEvict: Remove cache entries
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

    /**
     * Configure Redis-backed cache manager with TTL policies
     * 
     * @param connectionFactory Redis connection factory (auto-configured by Spring Boot)
     * @return CacheManager configured for Azure Cache for Redis
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Default cache configuration with 30-minute TTL
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
                )
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer())
                )
                .disableCachingNullValues();

        // Specific cache configuration for booking cache with 1-hour TTL
        RedisCacheConfiguration bookingCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
                )
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer())
                )
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration("bookingCache", bookingCacheConfig)
                .transactionAware()
                .build();
    }
}
