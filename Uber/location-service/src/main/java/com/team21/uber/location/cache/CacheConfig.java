package com.team21.uber.location.cache;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Cache configuration with TTLs grouped by usage type:
 *  - 5min: search (F1), JSONB query (F5)
 *  - 10min: DTO (F3), report (F6), combined (F9)
 *  - 15min: relationship DTO (F8), get-by-id
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    @org.springframework.context.annotation.Primary
    public RedisCacheManager cacheManager(RedisConnectionFactory cf) {
        ObjectMapper om = new ObjectMapper();
        registerOptionalModule(om, "com.fasterxml.jackson.datatype.jsr310.JavaTimeModule");
        registerOptionalModule(om, "com.fasterxml.jackson.datatype.jdk8.Jdk8Module");
        registerOptionalModule(om, "com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module");
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        om.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        GenericJackson2JsonRedisSerializer json = new GenericJackson2JsonRedisSerializer(om);
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(json));

        Map<String, RedisCacheConfiguration> caches = Map.ofEntries(
                Map.entry("search-5m", base.entryTtl(Duration.ofMinutes(5))),
                Map.entry("jsonb-5m", base.entryTtl(Duration.ofMinutes(5))),
                Map.entry("dto-10m", base.entryTtl(Duration.ofMinutes(10))),
                Map.entry("report-10m", base.entryTtl(Duration.ofMinutes(10))),
                Map.entry("combined-10m", base.entryTtl(Duration.ofMinutes(10))),
                Map.entry("relationship-15m", base.entryTtl(Duration.ofMinutes(15))),
                Map.entry("entity-15m", base.entryTtl(Duration.ofMinutes(15)))
        );

        return RedisCacheManager.builder(cf)
                .cacheDefaults(base.entryTtl(Duration.ofMinutes(10)))
                .withInitialCacheConfigurations(caches)
                .build();
    }

    private static void registerOptionalModule(ObjectMapper om, String fqcn) {
        try {
            Class<?> c = Class.forName(fqcn);
            om.registerModule((com.fasterxml.jackson.databind.Module) c.getDeclaredConstructor().newInstance());
        } catch (Throwable ignored) {}
    }
}
