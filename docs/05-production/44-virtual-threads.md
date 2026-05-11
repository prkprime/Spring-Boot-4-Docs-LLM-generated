# Virtual Threads in Depth

Chapter 15 introduced virtual threads as JVM-managed threads that are cheap enough to create per task. They are still `Thread` instances, but they are not one-to-one wrappers around operating-system threads. When a virtual thread is doing Java work, it is mounted on a platform carrier thread. When it blocks in a way the JDK understands, it can unmount, freeing the carrier to run another virtual thread.

That is the core trade: virtual threads do not make a CPU faster, but they let a servlet application keep many more blocking operations in flight without dedicating one platform thread to each request. This chapter looks at the details that matter once you move past the switch: pinning, carrier behavior, connection pools, and a small reproducible load demo.

The sample uses Spring MVC, Actuator, and the Spring MVC test starter:

```xml
{% include-markdown "../../code/44-virtual-threads/maven/pom.xml" comments=false %}
```

## What Boot Enables

The switch is small:

```yaml
{% include-markdown "../../code/44-virtual-threads/maven/src/main/resources/application.yml" comments=false %}
```

`spring.threads.virtual.enabled=true` changes several defaults in a servlet Spring Boot application. Tomcat's request processing can use a virtual-thread executor. The application `TaskExecutor` runs tasks on virtual threads. `@Async` methods use virtual threads when they use Boot's auto-configured executor. MVC async return values, scheduled tasks, and framework integrations that use the application task executor get the same behavior.

The result is similar in spirit to:

```java
Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory())
```

You submit one task, and the JVM gives that task a fresh virtual thread. In the sample tests we assert the behavior instead of relying on an implementation class name, because Spring can wrap or adapt executors between releases.

The Tomcat setting remains in the YAML on purpose:

```yaml
server:
  tomcat:
    threads:
      max: 200
```

With platform-thread request handling, `max` is the maximum number of request worker threads. With virtual threads, the important ceiling shifts toward carrier capacity and the resources behind the request: database connections, HTTP client pools, file descriptors, and downstream rate limits. Do not read `max: 200` as "only 200 requests can block." A virtual-thread application can have far more requests waiting on I/O, which is useful only when the dependencies can tolerate that concurrency.

The actuator exposure is deliberately small:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
```

For local investigation, `/actuator/metrics` gives you JVM thread metrics and HTTP server metrics. In production you would usually add Prometheus or OTLP export, but the point here is the virtual-thread behavior, not a monitoring backend.

Pin tracing is not a Spring Boot logging level. Use it as a JVM option:

```bash
JAVA_TOOL_OPTIONS="-Djdk.tracePinnedThreads=full" ./mvnw spring-boot:run
```

If a virtual thread blocks while pinned, the JVM prints a diagnostic stack trace. For lower-noise production investigation, Java Flight Recorder has a `jdk.VirtualThreadPinned` event.

## Blocking Request Demo

Here is the smallest blocking endpoint:

```java
{% include-markdown "../../code/44-virtual-threads/maven/src/main/java/dev/springboot4docs/ch_44_virtual_threads/BlockingController.java" comments=false %}
```

`GET /sleep?ms=100` sleeps and returns the executing thread's `toString()`. On a virtual thread you will see text that contains `VirtualThread`, often with a carrier reference such as a `ForkJoinPool` worker. That carrier detail is diagnostic, not an API contract. The reliable fact is `Thread.currentThread().isVirtual()`.

The controller returns a `Callable<String>`. Spring MVC treats that as asynchronous request handling and runs the callable on the application task executor. That makes the chapter testable without opening a real port, and it also demonstrates the same executor Boot configures for application work. In a normal running servlet container, request processing itself can also use virtual threads when virtual threads are enabled.

Virtual threads help most when the work is concurrent blocking I/O: waiting for a database, an HTTP API, a file read, a message broker, a cache, or another service. A platform-thread servlet stack pays one OS thread per blocked request. A virtual-thread stack can park many blocked requests with much lower thread overhead. It is normal to have thousands, or even tens of thousands, of in-flight virtual threads when the work is mostly waiting.

They do not help CPU-bound work. If a request compresses a large file, performs image processing, signs thousands of tokens, or runs a search algorithm, it needs CPU time. Virtual threads do not create more cores. For CPU-heavy work, use a small bounded `ExecutorService` sized around available processors and queue deliberately.

Reactive code is also not the main target. WebFlux, R2DBC, and reactive HTTP clients are already built around non-blocking I/O and event loops. Adding virtual threads usually gives you another concurrency model without removing the existing one. Virtual threads are most valuable when your code is naturally direct and blocking.

## Pinning

Pinning is the edge that makes virtual threads worth understanding. A virtual thread is pinned when it cannot unmount from its carrier while blocked. The carrier is then stuck too, which partially recreates the platform-thread problem.

The sample has a deliberately bad endpoint:

```java
{% include-markdown "../../code/44-virtual-threads/maven/src/main/java/dev/springboot4docs/ch_44_virtual_threads/PinController.java" comments=false %}
```

The blocking call happens inside a `synchronized` block. On older JDKs, blocking while holding a monitor was a classic way to pin a virtual thread. Java 24 reduced pinning in important cases, so modern JDKs are less fragile than early virtual-thread releases. Still, the rule is useful: avoid doing slow blocking work while holding intrinsic locks. Keep synchronized sections tiny, or use higher-level concurrency tools where the JDK can park and unpark virtual threads cleanly.

Other pinning causes include native calls and some blocking operations that cannot be represented through `LockSupport` parking. Pinning is not automatically a bug. A short pinned section is usually irrelevant. A hot path that pins carriers for hundreds of milliseconds under load can collapse throughput.

Use two signals while investigating. `-Djdk.tracePinnedThreads=full` is excellent during development because it tells you where the pin happened. JFR is better for longer runs because it records structured events such as `jdk.VirtualThreadStart`, `jdk.VirtualThreadPinned`, and `jdk.VirtualThreadSubmitFailed` without flooding application logs.

The test for `/pin` does not try to scrape JVM diagnostic output. That output depends on how the JVM is launched, which makes it brittle in a unit test. Instead, the test proves the endpoint is executing on a virtual thread and the code documents the pinned pattern. Run the app with the JVM flag when you want to observe the warning directly.

## Load Without JMeter

The chapter includes a small load endpoint:

```java
{% include-markdown "../../code/44-virtual-threads/maven/src/main/java/dev/springboot4docs/ch_44_virtual_threads/LoadController.java" comments=false %}
```

`GET /load?requests=1000&sleepMs=100` starts a virtual-thread-per-task executor, submits many sleeping tasks, waits for them all, and returns the elapsed time plus a few sample thread names. This is not a full benchmark harness, but it is reproducible and JMeter-free.

Use it to build intuition. If you run 1,000 tasks that each sleep for 100 ms, the result should be much closer to 100 ms than to 100 seconds, plus scheduling and application overhead. The JVM can keep those sleeping virtual threads around cheaply. If you replace `Thread.sleep` with CPU-heavy work, the result changes completely: the tasks compete for the same finite cores.

The report includes platform thread counts before and after the run. JVM management APIs primarily expose platform threads, so the count should not jump by the number of virtual tasks. That is the practical observability point: one thousand virtual threads is not one thousand carrier threads.

For a more serious benchmark, use a separate client process so the load generator does not compete with the server in the same JVM. Warm the app first, keep payloads realistic, and test against real connection pools. A benchmark of `Thread.sleep` proves scheduling behavior; it does not prove your database, TLS stack, or downstream API can absorb the same concurrency.

## Tests

The tests use a full Spring context with mock MVC infrastructure:

```java
{% include-markdown "../../code/44-virtual-threads/maven/src/test/java/dev/springboot4docs/ch_44_virtual_threads/ApplicationTests.java" comments=false %}
```

The `/sleep` test performs the request, waits for MVC async processing, and checks that the response contains `VirtualThread`. The `/pin` test does the same for the pinned example and also checks the JSON `virtual` field. The executor test submits directly to the auto-configured `applicationTaskExecutor` and verifies that the task body ran on a virtual thread.

This combination proves the chapter's important behavior without asserting private implementation details. In a real server process, Tomcat participates too. In the test process, MVC async and the application executor give us deterministic virtual-thread execution without requiring a local socket.

Run the chapter from the Maven directory:

```bash
./mvnw -q -B test
```

## ThreadLocal And Scoped Values

`ThreadLocal` still works with virtual threads. Each virtual thread is a real `Thread` from the Java API's point of view, so thread-local state is visible inside that virtual thread.

The inheritance pattern changes in practice. Platform-thread pools reuse the same worker threads, so accidental thread-local leaks can survive between tasks. Virtual-thread-per-task execution starts a fresh virtual thread for each task, so old task-local values are less likely to leak forward. That does not make `ThreadLocal` a design goal. It still hides data flow and can create memory pressure if you attach large objects to huge numbers of virtual threads.

For new Java code that needs request-scoped context, prefer `ScopedValue` where it fits. Scoped values make the lifetime explicit: bind a value for a lexical scope, run work inside that scope, and let the binding disappear when the scope exits. Spring applications will still encounter `ThreadLocal` through logging MDC, transactions, security context, and framework integrations, but new application context should avoid unnecessary hidden mutation.

## Pool And Bulkhead Rules

Virtual threads make blocked callers cheap. They do not make dependencies infinite. That is the production rule.

Connection pools become more important, not less. If HikariCP has 10 connections and 2,000 virtual-thread requests try to query the database, only 10 can use a connection at once. The rest wait. That may be fine, or it may turn into a large queue that hides overload until latency explodes. The same applies to HTTP client pools, Redis connections, message consumers, and external APIs.

Add bulkheads where the dependency needs a hard concurrency limit. A bounded executor, semaphore, rate limiter, or connection pool is a deliberate back-pressure point. Without those limits, a virtual-thread servlet app can accept a huge number of blocked requests and move the failure from "thread pool exhausted" to "database saturated" or "downstream timed out."

Watch carrier health, mounted virtual-thread behavior, pinning rate, request latency, and dependency pool wait time. The exact dashboard depends on your runtime, but the questions are stable: are carriers blocked, are virtual threads mostly parked on I/O, are pin events frequent, and are downstream pools becoming the real queue?

Also check timeout policy. A cheap blocked thread is still a blocked request, and an abandoned client may keep work alive longer than you expect. Set server, client, database, and downstream timeouts deliberately so virtual-thread capacity does not become an excuse for infinite waiting.

Use virtual threads for direct, blocking application code. Keep CPU work bounded. Keep locks short. Size connection pools intentionally. Measure pinning with the JVM and JFR instead of guessing.

Chapter 45 moves from runtime behavior to packaging the application as a Docker image.
