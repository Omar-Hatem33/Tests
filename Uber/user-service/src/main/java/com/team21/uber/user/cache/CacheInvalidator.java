package com.team21.uber.user.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Wildcard cache invalidation using Redis SCAN + DEL.
 *
 * Uses StringRedisTemplate (always auto-configured by Spring Boot)
 * instead of RedisTemplate<String, Object> which may not exist as a bean,
 * causing getIfAvailable() to silently return null and skip all evictions.
 *
 * Soft-fails on any Redis error so the upstream Postgres transaction
 * is never rolled back due to a cache failure.
 */
@Component
public class CacheInvalidator {

    private static final Logger log = LoggerFactory.getLogger(CacheInvalidator.class);

    private final StringRedisTemplate redis;

    public CacheInvalidator(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Deletes all Redis keys matching the given glob pattern.
     * Uses SCAN to avoid blocking the server (safe for production).
     *
     * Examples:
     *   evictPattern("user-service::user::42")       — exact key
     *   evictPattern("user-service::S1-F1::*")       — all search cache entries
     *   evictPattern("user-service::S1-F12::*")      — all activity feed entries
     */
    public void evictPattern(String pattern) {
        try {
            Set<String> keys = new HashSet<>();

            try (Cursor<String> cursor = redis.scan(
                    ScanOptions.scanOptions()
                            .match(pattern)
                            .count(500)
                            .build()
            )) {
                while (cursor.hasNext()) {
                    keys.add(cursor.next());
                }
            }

            if (!keys.isEmpty()) {
                log.debug("Evicting {} Redis keys matching pattern: {}", keys.size(), pattern);
                redis.delete(keys);
            } else {
                log.debug("No Redis keys found for pattern: {}", pattern);
            }

        } catch (Exception ex) {
            log.warn("Cache invalidation failed for pattern '{}': {}", pattern, ex.getMessage());
        }
    }
}