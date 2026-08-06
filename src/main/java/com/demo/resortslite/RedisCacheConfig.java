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
import java.util.HashMap;
import java.util.Map;

/**
 * Redis Cache Configuration for Amazon ElastiCache
 * 
 * FIXED cr-java-0067 [State Management & Session Issues / Medium]: In-Memory Caching Without TTL
 * 
 * This configuration replaces unbounded in-memory caching (HashMap) with Amazon ElastiCache 
 * for Redis with proper TTL (Time-To-Live) policies.
 * 
 * Benefits:
 * - Controlled expiration: TTL prevents indefinite memory growth
 * - Consistent data: Cache is shared across all application instances
 * - Centralized management: Single cache cluster for all instances
 * - Automatic eviction: Expired entries are automatically removed
 * - Scalability: Redis handles cache operations independently of application memory
 * - High availability: ElastiCache provides automatic failover and replication
 * 
 * Cache Configuration:
 * - Default TTL: 15 minutes (900 seconds) - balances freshness with performance
 * - Booking cache TTL: 30 minutes (1800 seconds) - longer retention for booking data
 * - Serialization: JSON format for complex objects
 * - Key prefix: Prevents collisions with session data
 * 
 * AWS ElastiCache Setup:
 * 1. Use the same ElastiCache cluster configured for session management
 * 2. Redis namespace separation ensures cache and session data don't conflict
 * 3. Configure REDIS_HOST, REDIS_PORT, and REDIS_PASSWORD via environment variables
 * 4. For production, enable encryption in-transit and at-rest
 * 
 * Usage:
 * - @Cacheable: Automatically cache method results
 * - @CacheEvict: Remove entries from cache
 * - @CachePut: Update cache entries
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

    /**
     * Configure Redis-backed cache manager with TTL policies
     * 
     * @param connectionFactory Redis connection factory (auto-configured by Spring Boot)
     * @return CacheManager configured for Amazon ElastiCache
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Default cache configuration with 15-minute TTL
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(15)) // Default TTL: 15 minutes
                .disableCachingNullValues() // Don't cache null values
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
                )
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer()
                        )
                )
                .prefixCacheNameWith("resorts-lite:cache:"); // Namespace prefix

        // Custom TTL configurations for specific caches
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // Booking cache: 30-minute TTL for booking data
        cacheConfigurations.put("bookingCache", 
                defaultConfig.entryTtl(Duration.ofMinutes(30)));
        
        // Availability cache: 5-minute TTL for frequently changing data
        cacheConfigurations.put("availabilityCache", 
                defaultConfig.entryTtl(Duration.ofMinutes(5)));
        
        // Report cache: 60-minute TTL for report data
        cacheConfigurations.put("reportCache", 
                defaultConfig.entryTtl(Duration.ofMinutes(60)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware() // Enable transaction support
                .build();
    }
}
