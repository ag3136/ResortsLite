package com.demo.resortslite;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis Session Configuration for Amazon ElastiCache
 * 
 * FIXED cr-java-0065 [State Management & Session Issues / High]: HTTP Session State Storage
 * 
 * This configuration enables Spring Session Data Redis to replace in-memory HTTP sessions
 * with distributed session storage backed by Amazon ElastiCache for Redis.
 * 
 * Benefits:
 * - Stateless application instances: Sessions are stored externally in Redis
 * - Horizontal scaling: Multiple EC2 instances can share session data
 * - High availability: ElastiCache provides automatic failover and replication
 * - Session persistence: Sessions survive application restarts and deployments
 * - Load balancer compatibility: AWS ALB can distribute requests across instances without sticky sessions
 * 
 * Configuration:
 * - Session timeout: 30 minutes (1800 seconds) - configured in application.properties
 * - Redis namespace: spring:session - prevents key collisions with other Redis data
 * - Flush mode: ON_SAVE - sessions are persisted immediately when modified
 * 
 * AWS ElastiCache Setup:
 * 1. Create an ElastiCache for Redis cluster in your VPC
 * 2. Configure security groups to allow access from your application instances
 * 3. Set REDIS_HOST environment variable to your ElastiCache endpoint
 * 4. For production, enable encryption in-transit (set REDIS_SSL=true)
 * 5. For production, enable authentication (set REDIS_PASSWORD)
 */
@Configuration
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 1800)
public class RedisSessionConfig {

    /**
     * Redis Template for session data serialization
     * Uses Jackson JSON serializer for complex objects stored in session
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Use String serializer for keys
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Use JSON serializer for values to support complex objects
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        
        template.afterPropertiesSet();
        return template;
    }
}
