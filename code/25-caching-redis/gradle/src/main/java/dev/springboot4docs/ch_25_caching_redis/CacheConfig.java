package dev.springboot4docs.ch_25_caching_redis;

import java.time.Duration;
import java.util.Map;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    RedisCacheConfiguration redisCacheConfiguration() {
        // Default serializer (JDK) is used here for simplicity; Jackson 3
        // values would be richer but require a configured ObjectMapper.
        return RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10));
    }

    @Bean
    RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory,
            RedisCacheConfiguration redisCacheConfiguration) {
        var cacheConfigurations = Map.of(
                "forecasts", redisCacheConfiguration.entryTtl(Duration.ofMinutes(10)),
                "quotes", redisCacheConfiguration.entryTtl(Duration.ofHours(1)));

        return RedisCacheManager.RedisCacheManagerBuilder.fromConnectionFactory(connectionFactory)
            .cacheDefaults(redisCacheConfiguration)
            .withInitialCacheConfigurations(cacheConfigurations)
            .build();
    }

}
