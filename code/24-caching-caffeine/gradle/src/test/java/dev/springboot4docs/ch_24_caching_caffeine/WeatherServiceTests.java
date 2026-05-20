package dev.springboot4docs.ch_24_caching_caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class WeatherServiceTests {

    @Autowired
    private WeatherService weatherService;

    @Autowired
    private WeatherApiClient weatherApiClient;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void resetState() {
        weatherApiClient.resetCallCount();
        var cache = cacheManager.getCache("forecasts");
        if (cache != null) cache.clear();
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

        assertThat(weatherService.update("London", "Forecast for London: rainy")).isEqualTo("Forecast for London: rainy");
        assertThat(weatherService.forecast("London")).isEqualTo("Forecast for London: rainy");
        assertThat(weatherApiClient.callCount()).isEqualTo(2);
    }

    @Test
    void caffeineStatisticsRecordCacheHits() {
        weatherService.forecast("Paris");
        weatherService.forecast("Paris");

        var cache = (CaffeineCache) cacheManager.getCache("forecasts");
        assertThat(cache).isNotNull();

        Cache<Object, Object> nativeCache = cache.getNativeCache();
        assertThat(nativeCache.stats().hitCount()).isPositive();
    }

}
