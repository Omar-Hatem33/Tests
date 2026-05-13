package com.team21.uber.location.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Wildcard cache invalidation backed by Redis SCAN.
 * Soft-fails when Redis is not configured.
 */
@Component
public class CacheInvalidator {

    private static final Logger log = LoggerFactory.getLogger(CacheInvalidator.class);

    private final ObjectProvider<RedisTemplate<String, Object>> redisProvider;

    public CacheInvalidator(ObjectProvider<RedisTemplate<String, Object>> redisProvider) {
        this.redisProvider = redisProvider;
    }

    public void evictPattern(String pattern) {
        try {
            RedisTemplate<String, Object> redis = redisProvider.getIfAvailable();
            if (redis == null) return;
            Set<String> keys = new HashSet<>();
            try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions().match(pattern).count(500).build())) {
                while (cursor.hasNext()) keys.add(cursor.next());
            }
            if (!keys.isEmpty()) redis.delete(keys);
        } catch (Exception ex) {
            log.warn("CacheInvalidator failed for pattern {}: {}", pattern, ex.getMessage());
        }
    }
}
