package com.demo.resortslite;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis Cache Configuration for Amazon ElastiCache
 * 
 * FIXED cr-java-0067: Configures RedisTemplate for distributed caching with Amazon ElastiCache.
 * 
 * This configuration:
 * - Provides RedisTemplate bean for application-level caching (separate from session management)
 * - Replaces static in-memory HashMap caches with distributed Redis-backed caching
 * - Enables cache consistency across all EC2 instances in the cluster
 * - Supports TTL-based expiration to prevent unbounded memory growth
 * - Uses JSON serialization for human-readable cache entries and cross-language compatibility
 * 
 * Benefits:
 * - Horizontal scalability: All instances share the same cache via ElastiCache
 * - Memory management: TTL policies automatically expire stale entries
 * - High availability: ElastiCache provides multi-AZ replication
 * - Performance: Sub-millisecond latency for cache operations
 * - Monitoring: CloudWatch metrics for cache hit/miss rates and memory usage
 * 
 * Configuration properties in application.properties:
 * - spring.redis.host: ElastiCache cluster endpoint (externalized via AWS Parameter Store)
 * - spring.redis.port: Redis port (default 6379)
 * - spring.redis.password: ElastiCache auth token (externalized via AWS Secrets Manager)
 * - app.cache.booking.ttl.minutes: Cache entry TTL in minutes (default 60)
 */
@Configuration
public class RedisCacheConfig {

    /**
     * Configures RedisTemplate with JSON serialization for cache values.
     * 
     * @param connectionFactory Redis connection factory (auto-configured by Spring Boot)
     * @return Configured RedisTemplate for caching operations
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Use String serialization for cache keys (e.g., "booking:BK-12345678")
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Use JSON serialization for cache values (booking objects, etc.)
        // This allows human-readable cache entries and cross-language compatibility
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        
        template.afterPropertiesSet();
        return template;
    }
}
