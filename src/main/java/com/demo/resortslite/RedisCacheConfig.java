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
 * Redis Cache Configuration for Google Cloud Memorystore
 * FIXED cr-java-0067: Replaces in-memory cache with Redis-backed distributed cache
 * 
 * This configuration:
 * - Enables Spring Cache abstraction with Redis as the backing store
 * - Configures TTL (Time-To-Live) for cache entries to prevent memory exhaustion
 * - Ensures cache consistency across all application instances in GCP
 * - Supports horizontal scaling and auto-scaling without cache synchronization issues
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

    /**
     * Configure Redis-backed cache manager with TTL
     * 
     * @param connectionFactory Redis connection factory (auto-configured by Spring Boot)
     * @return CacheManager configured for Google Cloud Memorystore
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Configure cache with 30-minute TTL to prevent indefinite memory growth
        RedisCacheConfiguration cacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))  // TTL prevents stale data and memory exhaustion
                .disableCachingNullValues()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer()));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(cacheConfig)
                .transactionAware()
                .build();
    }
}
