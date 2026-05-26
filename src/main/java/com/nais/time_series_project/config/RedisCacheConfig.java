package com.nais.time_series_project.config;

import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis cache configuration using Spring Data Redis 4.x and Jackson 3.
 *
 * GenericJacksonJsonRedisSerializer (without "2") is the Jackson 3-based serializer
 * introduced in Spring Data Redis 4.0 as the replacement for the deprecated
 * GenericJackson2JsonRedisSerializer. Built via its builder to avoid deprecated constructors.
 *
 * enableDefaultTyping embeds "@class" type hints so that polymorphic values
 * (List, Map<String,Object>, Instant, Double...) round-trip correctly through Redis.
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

    @Value("${cache.query-ttl-minutes}")
    private int ttlMinutes;

    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(Object.class)
                .build();

        GenericJacksonJsonRedisSerializer jsonSerializer = GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(typeValidator)
                .build();

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(ttlMinutes))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer));
    }
}
