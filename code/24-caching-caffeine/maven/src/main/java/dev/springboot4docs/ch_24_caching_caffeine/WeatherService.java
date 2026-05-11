package dev.springboot4docs.ch_24_caching_caffeine;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class WeatherService {

	private final WeatherApiClient weatherApiClient;

	WeatherService(WeatherApiClient weatherApiClient) {
		this.weatherApiClient = weatherApiClient;
	}

	@Cacheable(cacheNames = "forecasts", key = "#city")
	public String forecast(String city) {
		return weatherApiClient.forecast(city);
	}

	@CacheEvict(cacheNames = "forecasts", key = "#city")
	public void evict(String city) {
	}

	@CachePut(cacheNames = "forecasts", key = "#city")
	public String update(String city, String forecast) {
		return forecast;
	}

}
