package com.demo.resortslite;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * RedisSessionConfig — configures Spring Session backed by Amazon ElastiCache for Redis.
 *
 * <p>FIX cr-java-0065 (blockers 13–17): {@code @EnableRedisHttpSession} replaces the
 * default in-memory {@code HttpSession} store with a Redis-backed distributed session
 * store. This enables stateless application instances that can be horizontally scaled
 * behind an AWS Application Load Balancer without sticky sessions.
 *
 * <p>FIX cr-java-0067 (blocker 20): The {@link RedisTemplate} bean provides the
 * distributed cache used by {@link BookingController} to replace the unbounded
 * in-memory {@code HashMap}. All cache entries are written with explicit TTLs.
 *
 * <p>Connection details (host, port, password, SSL) are injected via environment
 * variables {@code REDIS_HOST}, {@code REDIS_PORT}, {@code REDIS_PASSWORD}, and
 * {@code REDIS_SSL} — see {@code application.properties}.
 */
@Configuration
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 3600)
public class RedisSessionConfig {

    /**
     * Configures a {@link RedisTemplate} with JSON serialisation so that booking
     * objects stored in the cache are human-readable and cross-language compatible.
     *
     * @param connectionFactory auto-configured by Spring Boot from application.properties
     * @return a fully configured {@link RedisTemplate}
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
