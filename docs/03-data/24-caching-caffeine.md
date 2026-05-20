# Caching with Caffeine

Caching is one of the simplest ways to make a read-heavy application feel faster. If a method repeats slow work for the same input, cache the result and reuse it until the value is stale. Good candidates include expensive joins, report summaries, third-party API responses, feature-flag lookups, and reference data that changes less often than it is read.

This chapter uses Spring's caching abstraction with Caffeine as the in-process backend. Spring supplies the annotations and interception model. Caffeine supplies the actual cache: fast local storage with size limits, expiry policies, and statistics.

The example is deliberately small. A fake weather API client counts how many times it is called. The service method is cached. The tests prove the cache works by checking that repeated service calls do not repeatedly call the underlying client.

## Dependency

The chapter project already includes Spring Boot's cache starter. Add Caffeine without a version:

```xml
{% include-markdown "../../code/24-caching-caffeine/maven/pom.xml" comments=false %}
```

Spring Boot manages the Caffeine version, so the application stays aligned with the Boot 4.0.6 dependency set. When the Caffeine library is on the classpath, Boot can auto-detect it as a cache provider. This chapter also sets the cache type explicitly in configuration so the choice is visible:

```yaml
{% include-markdown "../../code/24-caching-caffeine/maven/src/main/resources/application.yml" comments=false %}
```

Spring's abstraction can sit over several providers: Caffeine, Redis, the simple concurrent-map cache, Ehcache, JCache providers, and others. The code that uses `@Cacheable` usually does not care which backend is underneath. That is the point of the abstraction: application services describe what should be cached, while configuration decides where and how the cached entries live.

Caffeine is a strong default for process-local caching. It is the modern successor to Guava's cache and is generally preferred for new Java applications. It is fast, actively maintained, and designed around practical controls such as maximum size, time-based expiry, and useful runtime stats.

## Cache Configuration

Caching is opt-in. Add `@EnableCaching`, then expose a cache manager:

```java
{% include-markdown "../../code/24-caching-caffeine/maven/src/main/java/dev/springboot4docs/ch_24_caching_caffeine/CacheConfig.java" comments=false %}
```

`@EnableCaching` tells Spring to create the infrastructure that intercepts cache annotations. That interception is proxy based, similar to `@Transactional`. Calls that enter the bean through the Spring proxy can be cached. Direct self-invocation inside the same bean bypasses the proxy and therefore bypasses caching.

`CaffeineCacheManager` is the Spring cache manager for Caffeine. This example registers one cache named `forecasts`. The cache has three important settings:

`maximumSize(100)` keeps the cache bounded. Local caches should always have a size limit unless the key space is tiny and known. Without a bound, a cache can become a memory leak.

`expireAfterWrite(Duration.ofMinutes(10))` expires entries ten minutes after they are written. This is useful for data that can be reused briefly but should not live forever.

`recordStats()` enables counters for hits, misses, evictions, and related metrics. It has a small overhead, but it is worth enabling in demos and often useful in production when cache behavior matters.

Caffeine also supports `expireAfterAccess`, which resets the expiry clock whenever an entry is read. That fits values that should stay hot while actively used. `expireAfterWrite` fits values that should be refreshed on a predictable age, even if they are popular.

## The Slow Dependency

The fake API client is a normal Spring service:

```java
{% include-markdown "../../code/24-caching-caffeine/maven/src/main/java/dev/springboot4docs/ch_24_caching_caffeine/WeatherApiClient.java" comments=false %}
```

`forecast(String city)` increments an `AtomicInteger` and returns a deterministic forecast string. The counter is not application logic. It is a testing hook that lets us prove whether the expensive operation ran.

This is the cleanest way to test caching. Avoid testing the cache by measuring elapsed time. Time-based tests are noisy and can pass or fail for reasons unrelated to caching. Count the underlying work instead. If the first call increments the counter and the second call for the same key does not, the cache is doing its job.

## Cached Service Methods

The service contains the annotation-driven cache behavior:

```java
{% include-markdown "../../code/24-caching-caffeine/maven/src/main/java/dev/springboot4docs/ch_24_caching_caffeine/WeatherService.java" comments=false %}
```

`@Cacheable(cacheNames = "forecasts", key = "#city")` means: before calling this method body, look in the `forecasts` cache using the city as the key. If an entry exists, return it and skip the method body. If no entry exists, call the method, store the result, and return it.

Spring can generate a key automatically from all method parameters. For a single-argument method, that default would be fine. The example still uses `key = "#city"` because explicit keys are easier to read in documentation and safer when methods grow additional parameters later.

The `key` expression uses SpEL. You can refer to parameter names, call simple methods, or combine fields:

```java
@Cacheable(cacheNames = "forecasts", key = "#city.toLowerCase()")
```

Use this power carefully. Cache keys should be stable and obvious. If `"London"` and `"london"` should mean the same thing, normalize the key or normalize the input before calling the cached method.

`@CacheEvict` removes an entry. In this chapter, `evict("London")` removes the cached London forecast so the next `forecast("London")` call goes back to the client.

`@CachePut` always calls the method and stores its return value in the cache. It is useful when the application already has a fresh value and wants to replace the cached one. Here, `update(city, forecast)` returns the supplied forecast and puts it under the same city key. The next read comes from the cache without invoking the client.

Spring also provides `@Caching`, which composes several cache operations on one method. Use it when a write needs to evict or update more than one cache entry.

Conditional caching is available when not every call should be cached:

```java
@Cacheable(cacheNames = "forecasts", key = "#city", condition = "#city != null", unless = "#result == null")
```

`condition` is checked before the method runs. `unless` is checked after the method returns. That makes `unless` the right place to avoid storing a result that turned out to be `null`, empty, or otherwise not worth caching.

## Web Endpoint

The controller is intentionally thin:

```java
{% include-markdown "../../code/24-caching-caffeine/maven/src/main/java/dev/springboot4docs/ch_24_caching_caffeine/WeatherController.java" comments=false %}
```

`GET /weather/{city}` delegates to the service. The controller does not know whether the result came from Caffeine, Redis, a direct API call, or a test double. That boundary matters. Caching is a service concern here because the service owns the expensive dependency and the key decision.

Start the app from the chapter directory:

=== "Maven"
    ```bash
    ./mvnw spring-boot:run
    ```

=== "Gradle"
    ```bash
    ./gradlew bootRun
    ```

Then call the endpoint:

```bash
curl http://localhost:8080/weather/London
```

The first call computes and stores the forecast. Repeating the same request within the expiry window returns the cached value.

## Tests

Cache annotations need the real Spring proxy machinery, so the behavior test uses `@SpringBootTest`:

```java
{% include-markdown "../../code/24-caching-caffeine/maven/src/test/java/dev/springboot4docs/ch_24_caching_caffeine/WeatherServiceTests.java" comments=false %}
```

The first test walks through the lifecycle:

First call: `weatherService.forecast("London")` misses the cache, delegates to `WeatherApiClient`, and increments the client counter to `1`.

Second call: the same city hits the cache. The returned forecast is the same, but the counter stays at `1`.

Evict: `weatherService.evict("London")` removes the entry.

Third call: the cache misses again, so the client counter moves to `2`.

Update: `weatherService.update("London", "Forecast for London: rainy")` writes the new value into the cache. The following `forecast("London")` returns `"Forecast for London: rainy"` and the client counter still stays at `2`.

The second test reaches through Spring's `CaffeineCache` wrapper to the native Caffeine `Cache` and asserts that `stats().hitCount()` is positive. That is a useful pattern when you need to prove cache behavior without depending on private implementation details. In a production system, the same stats can feed logs, metrics, or dashboards.

There is also a small web slice test for the controller:

```java
{% include-markdown "../../code/24-caching-caffeine/maven/src/test/java/dev/springboot4docs/ch_24_caching_caffeine/WeatherControllerTests.java" comments=false %}
```

The important Spring Boot 4 imports are visible here: `@WebMvcTest` comes from `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`, and `@MockitoBean` comes from `org.springframework.test.context.bean.override.mockito.MockitoBean`.

Run the chapter tests from the Maven project:

=== "Maven"
    ```bash
    ./mvnw test
    ```

=== "Gradle"
    ```bash
    ./gradlew test
    ```

## Footguns

The biggest caching surprise is self-invocation. If a method in `WeatherService` calls another cached method on `this`, the call does not pass through Spring's proxy. The annotation will not run. Put cached operations behind methods that are called from another Spring bean, or split responsibilities so the proxy boundary is real.

Do not mutate cached values after returning them. In-process caches usually store object references. If you cache a mutable list and one caller adds an element, the next caller can see that modified list. Prefer immutable values, records, defensive copies, or DTOs that your code treats as read-only.

Be deliberate about `null`. Depending on configuration, a cache can store a null-like placeholder. If null means "temporary failure" or "not found right now," caching it may hide future successful results until the entry expires. Use `unless = "#result == null"` when nulls should not be remembered.

Remember that Caffeine is process-local. It helps only inside the current JVM. If the application runs three replicas behind a load balancer, each replica has its own cache and its own entries. That is often fine for cheap, short-lived acceleration. It is not a shared consistency mechanism.

For a cache shared across application instances, use an external backend such as Redis. Chapter 25 moves from local Caffeine caching to Redis-backed caching for multi-instance applications.
