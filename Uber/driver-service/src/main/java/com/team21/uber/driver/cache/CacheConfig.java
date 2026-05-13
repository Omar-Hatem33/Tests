package com.team21.uber.driver.cache;

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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Cache configuration.
 * Cache names follow the spec key convention: driver-service::[featureId]
 * TTLs per spec:
 *  - 5 min:  search (F1, F5, F10)
 *  - 10 min: DTO (F3), report (F6), combined (F9), dashboard (F12)
 *  - 15 min: entity get-by-id (driver, document)
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    @org.springframework.context.annotation.Primary
    public RedisCacheManager cacheManager(RedisConnectionFactory cf) {
        ObjectMapper om = new ObjectMapper();
        registerModule(om, "com.fasterxml.jackson.datatype.jsr310.JavaTimeModule");
        registerModule(om, "com.fasterxml.jackson.datatype.jdk8.Jdk8Module");
        registerModule(om, "com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module");
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        om.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        // Custom serializer wrapping our configured ObjectMapper
        // Avoids all deprecated Spring Data Redis serializer classes
        RedisSerializer<Object> jsonSerializer = new RedisSerializer<>() {
            @Override
            public byte[] serialize(Object value) throws SerializationException {
                if (value == null) return new byte[0];
                try {
                    return om.writeValueAsBytes(value);
                } catch (Exception e) {
                    throw new SerializationException("Could not serialize: " + e.getMessage(), e);
                }
            }

            @Override
            public Object deserialize(byte[] bytes) throws SerializationException {
                if (bytes == null || bytes.length == 0) return null;
                try {
                    return om.readValue(bytes, Object.class);
                } catch (Exception e) {
                    throw new SerializationException("Could not deserialize: " + e.getMessage(), e);
                }
            }
        };

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(jsonSerializer));

        Map<String, RedisCacheConfiguration> caches = Map.ofEntries(
                // M1 feature caches
                Map.entry("driver-service::S2-F1",  base.entryTtl(Duration.ofMinutes(5))),
                Map.entry("driver-service::S2-F3",  base.entryTtl(Duration.ofMinutes(10))),
                Map.entry("driver-service::S2-F5",  base.entryTtl(Duration.ofMinutes(5))),
                Map.entry("driver-service::S2-F6",  base.entryTtl(Duration.ofMinutes(10))),
                Map.entry("driver-service::S2-F9",  base.entryTtl(Duration.ofMinutes(10))),
                // M2 feature caches
                Map.entry("driver-service::S2-F10", base.entryTtl(Duration.ofMinutes(5))),
                Map.entry("driver-service::S2-F12", base.entryTtl(Duration.ofMinutes(10))),
                // Entity get-by-id caches
                Map.entry("driver-service::driver",   base.entryTtl(Duration.ofMinutes(15))),
                Map.entry("driver-service::document", base.entryTtl(Duration.ofMinutes(15)))
        );

        return RedisCacheManager.builder(cf)
                .cacheDefaults(base.entryTtl(Duration.ofMinutes(10)))
                .withInitialCacheConfigurations(caches)
                .build();
    }

    /**
     * Explicit RedisTemplate bean typed as String/Object.
     * Spring Boot only auto-configures RedisTemplate of Object/Object by default,
     * so without this bean CacheInvalidator would receive null from its ObjectProvider.
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory cf) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(cf);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        return template;
    }

    private static void registerModule(ObjectMapper om, String className) {
        try {
            Class<?> c = Class.forName(className);
            om.registerModule((com.fasterxml.jackson.databind.Module)
                    c.getDeclaredConstructor().newInstance());
        } catch (Throwable ignored) {}
    }
}