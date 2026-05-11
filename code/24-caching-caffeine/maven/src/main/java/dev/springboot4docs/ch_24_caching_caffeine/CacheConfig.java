package dev.springboot4docs.ch_24_caching_caffeine;

import java.time.Duration;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

	@Bean
	CaffeineCacheManager cacheManager() {
		var cacheManager = new CaffeineCacheManager();
		var forecasts = new CaffeineCache("forecasts", Caffeine.newBuilder()
			.maximumSize(100)
			.expireAfterWrite(Duration.ofMinutes(10))
			.recordStats()
			.build());
		cacheManager.registerCustomCache("forecasts", forecasts.getNativeCache());
		return cacheManager;
	}

}
