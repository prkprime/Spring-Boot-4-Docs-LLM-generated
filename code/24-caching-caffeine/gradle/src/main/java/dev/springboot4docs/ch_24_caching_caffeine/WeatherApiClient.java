package dev.springboot4docs.ch_24_caching_caffeine;

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

    void resetCallCount() {
        callCount.set(0);
    }

}
