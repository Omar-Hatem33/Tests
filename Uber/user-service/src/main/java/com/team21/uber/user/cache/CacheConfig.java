package com.team21.uber.user.cache;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    @Primary
    public RedisCacheManager cacheManager(RedisConnectionFactory cf) {

        RedisSerializer<Object> json = RedisSerializer.json();

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith("user-service::")
                .disableCachingNullValues()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(json));

        return RedisCacheManager.builder(cf)
                .cacheDefaults(base.entryTtl(Duration.ofMinutes(10)))
                .withInitialCacheConfigurations(Map.ofEntries(

                        // ─── CRUD GET-by-ID (15 min) ───────────────────────────────────────
                        // Covers: GET /api/users/{id} and GET /api/saved-addresses/{id}
                        // Key pattern: user-service::user::{id}
                        //              user-service::saved-address::{id}
                        Map.entry("user",          base.entryTtl(Duration.ofMinutes(15))),
                        Map.entry("saved-address", base.entryTtl(Duration.ofMinutes(15))),

                        // ─── S1-F1  Search users (5 min) ───────────────────────────────────
                        // GET /api/users/search?name=&email=&role=
                        // Key pattern: user-service::S1-F1::{hash(name,email,role)}
                        Map.entry("S1-F1", base.entryTtl(Duration.ofMinutes(5))),

                        // ─── S1-F3  User ride summary DTO (10 min) ─────────────────────────
                        // GET /api/users/{id}/ride-summary
                        // Key pattern: user-service::S1-F3::{userId}
                        Map.entry("S1-F3", base.entryTtl(Duration.ofMinutes(10))),

                        // ─── S1-F5  Filter users by preference JSONB (5 min) ───────────────
                        // GET /api/users/filter?key=&value=
                        // Key pattern: user-service::S1-F5::{hash(key,value)}
                        Map.entry("S1-F5", base.entryTtl(Duration.ofMinutes(5))),

                        // ─── S1-F6  Top riders report (10 min) ─────────────────────────────
                        // GET /api/users/top-riders?startDate=&endDate=&limit=
                        // Key pattern: user-service::S1-F6::{hash(startDate,endDate,limit)}
                        Map.entry("S1-F6", base.entryTtl(Duration.ofMinutes(10))),

                        // ─── S1-F8  User profile with saved addresses (15 min) ─────────────
                        // GET /api/users/{id}/profile
                        // Key pattern: user-service::S1-F8::{userId}
                        Map.entry("S1-F8", base.entryTtl(Duration.ofMinutes(15))),

                        // ─── S1-F9  Users by language + min rides combined (10 min) ─────────
                        // GET /api/users/language?lang=&minRides=
                        // Key pattern: user-service::S1-F9::{hash(lang,minRides)}
                        Map.entry("S1-F9", base.entryTtl(Duration.ofMinutes(10))),

                        // ─── S1-F12 User activity feed (5 min) ─────────────────────────────
                        // GET /api/users/{id}/activity?page=&size=
                        // Key pattern: user-service::S1-F12::{userId}:{page}:{size}
                        Map.entry("S1-F12", base.entryTtl(Duration.ofMinutes(5)))

                ))
                .build();
    }
}