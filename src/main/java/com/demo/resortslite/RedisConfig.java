package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis Configuration for Distributed Caching
 * 
 * Fixed: cz-java-0070 - Local Caches
 * 
 * This configuration enables distributed caching using Google Cloud Memorystore for Redis
 * to replace local in-memory caches that don't work with horizontal pod scaling in GKE.
 * 
 * Benefits:
 * - Shared cache state across all GKE pod replicas
 * - Cache persistence during pod restarts and autoscaling events
 * - TTL-based automatic cache expiration
 * - High availability with Memorystore's built-in replication
 * 
 * Configuration via environment variables:
 * - REDIS_HOST: Redis server hostname (default: localhost)
 * - REDIS_PORT: Redis server port (default: 6379)
 * - REDIS_PASSWORD: Redis authentication password (optional, from Secret Manager)
 * 
 * For GKE deployment:
 * 1. Create Memorystore for Redis instance in same VPC as GKE cluster
 * 2. Configure VPC peering or Private Service Connect
 * 3. Inject REDIS_HOST via ConfigMap or environment variable
 * 4. Inject REDIS_PASSWORD via Secret Manager add-on or Kubernetes Secret
 * 5. Use Workload Identity for secure access to Secret Manager
 */
@Configuration
public class RedisConfig {

    @Value("${spring.redis.host:#{systemEnvironment['REDIS_HOST'] ?: 'localhost'}}")
    private String redisHost;

    @Value("${spring.redis.port:#{systemEnvironment['REDIS_PORT'] ?: '6379'}}")
    private int redisPort;

    @Value("${spring.redis.password:#{systemEnvironment['REDIS_PASSWORD'] ?: ''}}")
    private String redisPassword;

    /**
     * Configure Redis connection factory using Lettuce client
     * Lettuce provides connection pooling and async support
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);
        config.setPort(redisPort);
        
        // Set password only if provided (Memorystore can be configured with or without AUTH)
        if (redisPassword != null && !redisPassword.isEmpty()) {
            config.setPassword(redisPassword);
        }
        
        return new LettuceConnectionFactory(config);
    }

    /**
     * Configure RedisTemplate for object caching
     * Uses JSON serialization for complex objects (Map, List, etc.)
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Use String serializer for keys
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Use JSON serializer for values (supports Map, List, custom objects)
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        
        template.afterPropertiesSet();
        return template;
    }
}
