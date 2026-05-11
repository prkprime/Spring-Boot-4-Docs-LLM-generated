package dev.springboot4docs.ch_25_caching_redis;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;

@Service
public class WeatherApiClient {

	private final AtomicInteger callCount = new AtomicInteger();

	String forecast(String city) {
		callCount.incrementAndGet();
		return "Forecast for " + city + ": sunny";
	}

	int callCount() {
		return callCount.get();
	}

	void reset() {
		callCount.set(0);
	}

}
