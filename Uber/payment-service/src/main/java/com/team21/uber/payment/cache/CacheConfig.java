package com.team21.uber.payment.cache;

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
                .prefixCacheNameWith("payment-service::")
                .disableCachingNullValues()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(json));

        return RedisCacheManager.builder(cf)
                .cacheDefaults(base.entryTtl(Duration.ofMinutes(10)))
                .withInitialCacheConfigurations(Map.ofEntries(

                        // ─── CRUD GET-by-ID (15 min) ───────────────────────────────────────
                        // GET /api/payments/{id}
                        // Key pattern: payment-service::payment::{id}
                        Map.entry("payment",        base.entryTtl(Duration.ofMinutes(15))),

                        // GET /api/coupons/{id}
                        // Key pattern: payment-service::coupon::{id}
                        Map.entry("coupon",         base.entryTtl(Duration.ofMinutes(15))),

                        // GET /api/payment-coupons/{id}
                        // Key pattern: payment-service::payment-coupon::{id}
                        Map.entry("payment-coupon", base.entryTtl(Duration.ofMinutes(15))),

                        // ─── S5-F1  Search payments by status + date range (5 min) ──────────
                        // GET /api/payments/search?status=&startDate=&endDate=
                        // Key pattern: payment-service::S5-F1::{hash(status,startDate,endDate)}
                        // F1 = search → 5 min TTL
                        Map.entry("S5-F1", base.entryTtl(Duration.ofMinutes(5))),

                        // ─── S5-F3  User payment summary DTO (10 min) ───────────────────────
                        // GET /api/payments/user/{userId}/summary
                        // Key pattern: payment-service::S5-F3::{userId}
                        // F3 = DTO → 10 min TTL
                        Map.entry("S5-F3", base.entryTtl(Duration.ofMinutes(10))),

                        // ─── S5-F6  Revenue report by date range (10 min) ───────────────────
                        // GET /api/payments/reports/revenue?startDate=&endDate=
                        // Key pattern: payment-service::S5-F6::{hash(startDate,endDate)}
                        // F6 = report → 10 min TTL
                        Map.entry("S5-F6", base.entryTtl(Duration.ofMinutes(10))),

                        // ─── S5-F8  Payment details with applied coupons (15 min) ────────────
                        // GET /api/payments/{paymentId}/details
                        // Key pattern: payment-service::S5-F8::{paymentId}
                        // F8 = relationship DTO → 15 min TTL
                        Map.entry("S5-F8", base.entryTtl(Duration.ofMinutes(15))),

                        // ─── S5-F9  Most used coupons report (10 min) ────────────────────────
                        // GET /api/payments/coupons/top-used?limit=
                        // Key pattern: payment-service::S5-F9::{limit}
                        // F9 = combined → 10 min TTL
                        Map.entry("S5-F9", base.entryTtl(Duration.ofMinutes(10))),

                        // ─── S5-F10  Fare revenue by vehicle type (10 min) ───────────────────
                        // GET /api/payments/analytics/vehicle-type?startDate=&endDate=
                        // Key pattern: payment-service::S5-F10::{startDate}:{endDate}
                        Map.entry("S5-F10", base.entryTtl(Duration.ofMinutes(10)))

                ))
                .build();
    }
}