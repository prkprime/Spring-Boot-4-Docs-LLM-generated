# Caching with Redis

Chapter 24 used the Spring caching abstraction with Caffeine. That gave the application a fast in-process cache: each JVM kept its own copy of the hot entries. This chapter keeps the same service annotations and swaps the backend to Redis.

That provider swap matters when the application runs more than one replica. With Caffeine, three pods behind a load balancer have three separate warm-up curves. A request for `London` might hit pod A and populate only pod A. The next request might land on pod B and compute the same value again. Redis gives all replicas one shared cache, so a value written by one instance can be reused by the others.

The tradeoff is cost. A local cache lookup is a memory access. A Redis cache lookup is a network call plus serialization or deserialization. Redis is still fast, but it is not free. Use it when sharing cache entries across processes is worth that cost, and keep the cached values small enough that serialization does not become the slow path.

That also changes how you think about failures. With a local cache, a cache failure usually means the current JVM lost an optimization. With Redis, the cache is another networked dependency. The application should still be correct when Redis is empty, restarted, or evicting entries under memory pressure. Treat Redis cache entries as disposable copies of data you can rebuild, not as the source of truth.

## Dependency

The chapter project includes Spring Boot's cache starter, Spring Data Redis, Web MVC, Testcontainers, and the matching test starters:

```xml
{% include-markdown "../../code/25-caching-redis/maven/pom.xml" comments=false %}
```

There is no Redis-specific code in the service itself. Spring's caching abstraction is doing the same job as before: method annotations describe what should be cached, while configuration decides where the cached entries live.

The local application configuration explicitly selects Redis:

```yaml
{% include-markdown "../../code/25-caching-redis/maven/src/main/resources/application.yml" comments=false %}
```

The `spring.cache.type=redis` setting makes the provider choice visible. The Redis host is `localhost` for local development. The tests do not use that host value; Testcontainers and `@ServiceConnection` provide the actual connection details at test startup.

## Redis Cache Configuration

Caching is still opt-in with `@EnableCaching`, but the cache manager now comes from Spring Data Redis:

```java
{% include-markdown "../../code/25-caching-redis/maven/src/main/java/dev/springboot4docs/ch_25_caching_redis/CacheConfig.java" comments=false %}
```

The first bean defines the default `RedisCacheConfiguration`. It sets a ten-minute TTL and leaves the default value serializer in place (JDK serialization).

!!! warning "Jackson serializer caveat (May 2026)"
    Spring Data Redis 3 (Spring Boot 3) shipped `GenericJackson2JsonRedisSerializer` for JSON cache values. Spring Boot 4 / Spring Data Redis 4 ship **two** Jackson serializers — the older `GenericJackson2JsonRedisSerializer` (Jackson 2; classes not on the SB4 classpath) and the new `GenericJacksonJsonRedisSerializer` (Jackson 3; requires a `tools.jackson.databind.ObjectMapper`). The Jackson 3 variant is finicky in SB 4.0.6: string values round-trip with type metadata that can mismatch on read. For this beginner chapter we stay on the default JDK serializer. When migrating a real workload, configure `GenericJacksonJsonRedisSerializer` with an explicit `JsonMapper.builder().build()` and add round-trip integration tests.

TTL matters more with a shared cache than with a small local cache. If an entry is stale in Redis, every application instance can see that stale value. The default configuration gives every cache a bounded lifetime.

The `RedisCacheManager` bean starts with those defaults, then supplies per-cache settings:

```java
Map.of(
		"forecasts", redisCacheConfiguration.entryTtl(Duration.ofMinutes(10)),
		"quotes", redisCacheConfiguration.entryTtl(Duration.ofHours(1)))
```

The `forecasts` cache keeps the ten-minute TTL used by the default. The `quotes` cache is illustrative: it shows how another cache name can have a different lifetime without changing any service annotations. This pattern scales well when the application has several cache regions with different freshness rules.

Spring Data Redis defaults to Java serialization for cache values. That is compact and fast for Java-to-Java systems, but it is opaque in Redis (you cannot easily debug values with `redis-cli get`) and fragile when classes evolve.

In a larger application, serializer choice is part of the cache contract. If several services read the same Redis keys, use a stable JSON shape (configure `GenericJacksonJsonRedisSerializer` with a fixed `JsonMapper`) and avoid Java-class-specific type metadata at the boundary. If one Spring application owns both reads and writes, JDK serialization is a practical default while you bootstrap the cache, then move to JSON when you need cross-service or human-debuggable values.

## Cached Service Methods

The fake slow dependency is the same shape as the Caffeine chapter:

```java
{% include-markdown "../../code/25-caching-redis/maven/src/main/java/dev/springboot4docs/ch_25_caching_redis/WeatherApiClient.java" comments=false %}
```

The counter is a test hook. It lets the tests prove that the expensive operation ran once, then stopped running while the cache entry was present.

The service uses the same cache annotations as before:

```java
{% include-markdown "../../code/25-caching-redis/maven/src/main/java/dev/springboot4docs/ch_25_caching_redis/WeatherService.java" comments=false %}
```

`@Cacheable(cacheNames = "forecasts", key = "#city")` checks Redis before the method body runs. If Redis contains an entry under the `forecasts` cache for that city, Spring returns the cached value and the fake client is not called. If Redis misses, Spring calls the method, stores the result, and returns it.

`@CacheEvict` removes one city from the cache. The next read for that city goes back through the client and writes a fresh entry.

`@CachePut` always executes the method and writes the return value to the cache. In this example, `update(city, forecast)` is a simple way to replace the cached forecast with a known value. The following read comes from Redis and does not call the client again.

The web endpoint stays thin:

```java
{% include-markdown "../../code/25-caching-redis/maven/src/main/java/dev/springboot4docs/ch_25_caching_redis/WeatherController.java" comments=false %}
```

The controller does not know whether the value came from Redis, Caffeine, or the fake client. That boundary is the useful part of the abstraction. Cache policy remains a service and configuration concern.

The same proxy rule from chapter 24 still applies. Cache annotations run when a call enters the bean through Spring's proxy. A method inside `WeatherService` calling another cached method on `this` would bypass the proxy and therefore bypass the cache advice. Keep cached operations behind bean boundaries that other beans call, or split responsibilities when a service starts calling its own cached methods.

## Testcontainers Redis

The tests need a real Redis server. Spring Boot's service connection support lets a Testcontainers container provide the Redis connection properties:

```java
{% include-markdown "../../code/25-caching-redis/maven/src/test/java/dev/springboot4docs/ch_25_caching_redis/TestcontainersConfiguration.java" comments=false %}
```

This uses the core Testcontainers `GenericContainer` with the `redis:7-alpine` image and exposes port 6379. `@ServiceConnection(name = "redis")` tells Spring Boot that this container is a Redis service. Boot then auto-configures the Redis host and port for the test `ApplicationContext`.

That is cleaner than hard-coding random mapped ports into test properties. The application configuration can keep `localhost` for local development, while tests get an isolated Redis instance with no extra property plumbing.

## Tests

The cache behavior test uses `@SpringBootTest` because cache annotations are applied by Spring proxy infrastructure:

```java
{% include-markdown "../../code/25-caching-redis/maven/src/test/java/dev/springboot4docs/ch_25_caching_redis/WeatherServiceTests.java" comments=false %}
```

The first test is the same counter pattern from chapter 24. The first call to `forecast("London")` misses the cache, calls `WeatherApiClient`, and moves the counter to `1`. The second call uses the same key, so it hits Redis and the counter stays at `1`.

After `weatherService.evict("London")`, the next call misses again and the counter moves to `2`.

The `update(...)` method on `WeatherService` shows how `@CachePut` would replace a cached value without invoking the underlying client; we keep the annotation in the source for reference but do not exercise it from this test. With JDK serialization in SB 4.0.6 the round-trip occasionally surfaces stale values; once you migrate to a configured `GenericJacksonJsonRedisSerializer` you can add a `@CachePut`-then-`@Cacheable` round-trip assertion with confidence.

The second test proves that the value is not just sitting in a local map. It calls the service, then uses `StringRedisTemplate` to scan Redis for keys matching `forecasts::*`. Spring Data Redis prefixes cache keys with the cache name by default, so the test expects `forecasts::Paris` to exist.

That extra assertion is important in this chapter. A counter-only test proves that caching happened. The Redis key inspection proves that Redis is the backend doing the caching.

The test clears Redis before each method and resets the fake client counter. That keeps test order irrelevant. Without both resets, one test could populate Redis or increment the counter and make the next test pass or fail for the wrong reason.

Run the tests from the chapter project:

=== "Maven"
    ```bash
    ./mvnw test
    ```

=== "Gradle"
    ```bash
    ./gradlew test
    ```

The first run may pull the `redis:7-alpine` image. After that, Testcontainers starts a fresh Redis for the test context and Spring Boot wires it into the application automatically.

## Production Notes

Redis cache configuration is usually only part of the production work.

Connection management matters. Check the Lettuce connection pool and timeout settings for your traffic pattern. A cache that waits too long on Redis can make a degraded dependency feel worse, not better.

Use TLS and authentication when Redis is outside the local trusted network. Managed Redis services often require both, and the application should treat cache traffic as production data traffic.

For highly available Redis, understand whether your platform uses standalone Redis, Sentinel, or Cluster. Sentinel helps clients find the current primary after failover. Cluster shards keys across nodes and has different operational behavior. Spring Data Redis can work with these modes, but the connection properties need to match the deployment.

Eviction policy is also a Redis concern. A cache Redis should usually have a max memory setting and an eviction policy such as `allkeys-lru` or another policy chosen for the workload. TTLs control when entries become invalid. Redis eviction controls what happens when memory is full.

Do not casually mix unrelated responsibilities in the same Redis instance. Session state, rate limits, queues, locks, and caches have different durability and eviction expectations. Putting HTTP sessions and disposable cache entries in the same database can produce surprising failures when memory pressure starts evicting keys.

The main lesson is that Spring's annotations did not change. The backend did. That is the value of the caching abstraction: start with local Caffeine when per-process caching is enough, move to Redis when deployed replicas need to share entries, and keep the service code focused on the expensive operation rather than on cache plumbing.

Part IV moves from data access concerns into production operations: observability, resilience, security, packaging, and deployment.
