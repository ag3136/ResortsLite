package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
public class AzureRedisStateService {

    private final StringRedisTemplate redisTemplate;
    private final long ttlMinutes;

    public AzureRedisStateService(
            StringRedisTemplate redisTemplate,
            @Value("${app.redis.ttl-minutes:30}") long ttlMinutes) {
        this.redisTemplate = redisTemplate;
        this.ttlMinutes = ttlMinutes;
    }

    public void storeBooking(String bookingId, Map<String, Object> booking) {
        String key = bookingKey(bookingId);
        Map<String, String> values = new HashMap<>();
        for (Map.Entry<String, Object> entry : booking.entrySet()) {
            values.put(entry.getKey(), entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
        }
        redisTemplate.opsForHash().putAll(key, values);
        redisTemplate.expire(key, Duration.ofMinutes(ttlMinutes));
    }

    public Map<String, Object> getBooking(String bookingId) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(bookingKey(bookingId));
        if (entries == null || entries.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    public void storeSessionAttribute(String sessionId, String attributeName, String value) {
        String key = sessionKey(sessionId);
        redisTemplate.opsForHash().put(key, attributeName, value == null ? "" : value);
        redisTemplate.expire(key, Duration.ofMinutes(ttlMinutes));
    }

    public String getSessionAttribute(String sessionId, String attributeName) {
        Object value = redisTemplate.opsForHash().get(sessionKey(sessionId), attributeName);
        return value == null ? null : String.valueOf(value);
    }

    private String bookingKey(String bookingId) {
        return "booking:" + bookingId;
    }

    private String sessionKey(String sessionId) {
        return "session:" + sessionId;
    }
}
