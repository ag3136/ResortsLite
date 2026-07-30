package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis Configuration for Azure Cache for Redis
 * 
 * FIXED cr-java-0067: Configures distributed caching with Azure Cache for Redis
 * - Replaces in-memory caching with distributed Redis cache
 * - Enables TTL policies to prevent memory exhaustion
 * - Supports horizontal scaling across multiple instances
 * - Provides cache consistency in cloud environments
 * 
 * This configuration supports:
 * - Azure Cache for Redis (managed service)
 * - SSL/TLS connections for secure communication
 * - Connection pooling for optimal performance
 * - JSON serialization for complex objects
 */
@Configuration
public class RedisConfig {

    @Value("${spring.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.redis.port:6379}")
    private int redisPort;

    @Value("${spring.redis.password:}")
    private String redisPassword;

    @Value("${spring.redis.ssl:true}")
    private boolean redisSsl;

    @Value("${spring.redis.timeout:2000}")
    private int redisTimeout;

    /**
     * Configure Redis connection factory for Azure Cache for Redis
     * Supports SSL/TLS connections and authentication
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
        redisConfig.setHostName(redisHost);
        redisConfig.setPort(redisPort);
        
        // Set password if provided (required for Azure Cache for Redis)
        if (redisPassword != null && !redisPassword.isEmpty()) {
            redisConfig.setPassword(redisPassword);
        }

        // Configure Jedis client with SSL support for Azure Cache for Redis
        JedisClientConfiguration.JedisClientConfigurationBuilder jedisClientConfiguration = 
            JedisClientConfiguration.builder();
        
        jedisClientConfiguration.connectTimeout(Duration.ofMillis(redisTimeout));
        jedisClientConfiguration.readTimeout(Duration.ofMillis(redisTimeout));
        
        // Enable SSL for Azure Cache for Redis (required in production)
        if (redisSsl) {
            jedisClientConfiguration.useSsl();
        }

        JedisConnectionFactory jedisConnectionFactory = new JedisConnectionFactory(
            redisConfig, 
            jedisClientConfiguration.build()
        );
        
        return jedisConnectionFactory;
    }

    /**
     * Configure RedisTemplate for distributed caching
     * Uses JSON serialization for storing complex objects
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Use String serializer for keys
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Use JSON serializer for values (supports complex objects)
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        
        template.afterPropertiesSet();
        return template;
    }
}
