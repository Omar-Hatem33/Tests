package com.team21.uber.contracts.lock;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Distributed lock via Redis SETNX.
 * Closes the concurrency window in driver-OFFLINE transition and ride driver-assignment.
 */
@Service
public class RedisLockService {

    private final StringRedisTemplate redis;

    public RedisLockService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String tryAcquire(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redis.opsForValue().setIfAbsent(key, token, ttl);
        return Boolean.TRUE.equals(acquired) ? token : null;
    }

    public boolean release(String key, String token) {
        if (token == null) return false;
        String current = redis.opsForValue().get(key);
        if (token.equals(current)) {
            return Boolean.TRUE.equals(redis.delete(key));
        }
        return false;
    }
}
