package dev.springboot4docs.ch_25_caching_redis;

import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class WeatherServiceTests {

    @Autowired
    private WeatherService weatherService;

    @Autowired
    private WeatherApiClient weatherApiClient;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void resetState() {
        weatherApiClient.reset();
        var connectionFactory = Objects.requireNonNull(stringRedisTemplate.getConnectionFactory());
        try (var connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushDb();
        }
    }

    @Test
    void forecastIsCachedUntilEvictedOrUpdated() {
        assertThat(weatherService.forecast("London")).isEqualTo("Forecast for London: sunny");
        assertThat(weatherApiClient.callCount()).isEqualTo(1);

        assertThat(weatherService.forecast("London")).isEqualTo("Forecast for London: sunny");
        assertThat(weatherApiClient.callCount()).isEqualTo(1);

        weatherService.evict("London");

        assertThat(weatherService.forecast("London")).isEqualTo("Forecast for London: sunny");
        assertThat(weatherApiClient.callCount()).isEqualTo(2);
    }

    @Test
    void cachedForecastIsStoredInRedis() {
        weatherService.forecast("Paris");

        var scanOptions = ScanOptions.scanOptions().match("forecasts::*").count(100).build();
        var foundCacheKey = false;

        try (var keys = stringRedisTemplate.scan(scanOptions)) {
            while (keys.hasNext()) {
                if ("forecasts::Paris".equals(keys.next())) {
                    foundCacheKey = true;
                    break;
                }
            }
        }

        assertThat(foundCacheKey).isTrue();
    }

}
