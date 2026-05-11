# Async, SSE, And Virtual Threads

This chapter puts three related ideas beside each other because they are often confused:

- returning a `CompletableFuture` from a Spring MVC controller
- returning an `SseEmitter` for server-sent events
- turning on virtual threads in Spring Boot 4

They all touch request handling and concurrency, but they solve different problems. Async MVC is about releasing the servlet request thread while a response is still pending. SSE is about keeping one HTTP response open so the server can push events to the client. Virtual threads are about making blocking code cheap enough that you do not need to contort ordinary request handling into callback-style code just to survive many concurrent waits.

The sample project is a normal Spring Boot 4 WebMVC application:

```xml
{% include-markdown "../../code/15-async-sse-virtual-threads/maven/pom.xml" comments=false %}
```

## The Three Primitives

Servlet 6.1 supports asynchronous request processing. In Spring MVC, you usually do not touch the raw servlet async API. You return a supported async type from the controller, such as `Callable`, `CompletableFuture`, `DeferredResult`, `ResponseBodyEmitter`, `SseEmitter`, or `StreamingResponseBody`.

When a controller returns a `CompletableFuture`, Spring MVC starts async processing for that request. The original servlet request thread is released back to the container. When the future completes, Spring MVC dispatches back into the MVC pipeline and writes the response as if the controller had returned the value directly.

That does not make the work free. It runs somewhere else. In this chapter, the "somewhere else" is the executor used by `CompletableFuture.supplyAsync(...)`. In a real application it might be a service call, database call, message broker result, or a carefully chosen application executor. Async MVC changes how the HTTP request waits for a result; it does not eliminate the work.

`@Async` is a different feature. It tells Spring to run an annotated method on an executor when that method is called through a Spring bean proxy. That is useful for background work, fan-out, fire-and-forget tasks, and service methods that should not run on the caller's thread. It is not the main tool for making an HTTP controller response asynchronous. You can combine `@Async` with controller code when there is a real reason, but this chapter keeps the HTTP examples focused on MVC's return-value support.

SSE is related because `SseEmitter` also uses MVC async request handling, but the outcome is different. A `CompletableFuture<Job>` produces one JSON response. An `SseEmitter` keeps sending frames on a `text/event-stream` response until the server completes the emitter, the client disconnects, or a timeout/error occurs.

## Virtual Threads

!!! note "Spring Boot 4 virtual threads are opt-in"
    Spring Boot 4 does not turn virtual threads on automatically. This chapter opts in with `spring.threads.virtual.enabled=true`.

The application enables virtual threads in YAML:

```yaml
{% include-markdown "../../code/15-async-sse-virtual-threads/maven/src/main/resources/application.yml" comments=false %}
```

In a servlet MVC application, enabling this option lets the embedded web server handle requests on virtual threads. Boot also uses virtual threads for its application task executor, which affects infrastructure such as `@Async`. Scheduled tasks and blocking HTTP clients managed by Boot, including `RestClient` and HTTP service clients, can also participate in the virtual-thread setup.

The practical reason to care is blocking I/O. Many business endpoints spend most of their time waiting for a database, a downstream HTTP service, a file store, or a remote API. With platform threads, thousands of concurrent blocking waits can consume thousands of expensive OS-backed threads. With virtual threads, each blocking wait is still a wait, but the thread abstraction is much cheaper. That means straightforward blocking code becomes viable at higher concurrency.

This changes the usual "async for scalability" argument. Before virtual threads, you might return `CompletableFuture` or switch to a reactive stack because tying a platform thread to every slow downstream call was too costly. With virtual threads, most ordinary blocking request handlers can stay direct:

```java
@GetMapping("/orders/{id}")
Order order(@PathVariable Long id) {
	return this.orders.findById(id);
}
```

That shape is often easier to read, debug, profile, and test than a chain of callbacks. Virtual threads do not make the database faster, do not increase the size of your connection pool, and do not remove rate limits in downstream systems. They just make the waiting thread cheap.

Async return values still belong in the toolbox. Use them when the response is genuinely completed by something outside the current call stack: a future returned by another API, a callback from a message broker, a long-running computation that is already managed elsewhere, or a streaming response that will produce data over time. Do not use them only to hide a normal blocking call. In a virtual-thread MVC app, direct blocking code is often the most honest expression of the request.

They also do not help every workload. Pure CPU work is still CPU work. If an endpoint burns 500 ms of CPU, virtual threads do not create more cores. Reactive code is already organized around non-blocking I/O, so virtual threads are not a magic upgrade there either. They matter most when the code is simple, blocking, and I/O-heavy.

Compatibility footguns still exist. A virtual thread can be pinned to its carrier thread while executing some `synchronized` blocks or native/JNI calls, which reduces the scalability benefit. Thread locals mostly work as expected, but you should still avoid treating them as a place to hide broad mutable request state. Libraries that assume a small, fixed number of long-lived threads may need review.

## The Controller

The response type for the JSON endpoint is deliberately tiny:

```java
{% include-markdown "../../code/15-async-sse-virtual-threads/maven/src/main/java/dev/springboot4docs/ch_15_async_sse_virtual_threads/Job.java" comments=false %}
```

The controller exposes both examples:

```java
{% include-markdown "../../code/15-async-sse-virtual-threads/maven/src/main/java/dev/springboot4docs/ch_15_async_sse_virtual_threads/JobsController.java" comments=false %}
```

The `GET /jobs/{id}` endpoint returns `CompletableFuture<Job>`:

```java
@GetMapping("/jobs/{id}")
CompletableFuture<Job> job(@PathVariable Long id) {
	return CompletableFuture.supplyAsync(() -> {
		sleep(200);
		return new Job(id, "complete");
	});
}
```

The `sleep(200)` call is only there to make the asynchronous boundary visible. Spring MVC sees the `CompletableFuture`, starts async request processing, and waits for the future to complete. When the future produces `new Job(id, "complete")`, MVC serializes that record to JSON using the normal HTTP message conversion path.

This is useful when the result naturally arrives later from another computation or callback source. It is less compelling as a blanket scalability trick in a Boot 4 application that has virtual threads enabled. If the real code is just "call a blocking repository and return the result", a direct controller method on a virtual request thread is usually the clearer design.

The `GET /events` endpoint returns `SseEmitter`:

```java
@GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
SseEmitter events() {
	SseEmitter emitter = new SseEmitter(30_000L);
	AtomicInteger counter = new AtomicInteger();
	AtomicReference<ScheduledFuture<?>> task = new AtomicReference<>();
```

The response content type is `text/event-stream`. The emitter sends five named events, one per second:

```java
emitter.send(SseEmitter.event()
		.name("tick")
		.data("tick-" + tick));
```

On the wire, an SSE event is just text frames with fields such as `event:` and `data:`. A named event with string data looks like this:

```text
event:tick
data:tick-1

```

Browsers consume this format with `EventSource`. The connection is one-way: server to client. The browser automatically reconnects if the stream drops, and the server can use event ids when it needs resume behavior.

SSE is a good fit for progress updates, notifications, status feeds, dashboards, logs, and simple server push. Use WebSockets when you need two-way communication, client-to-server messages on the same connection, custom binary protocols, or low-latency bidirectional interaction. Do not choose WebSockets just because "streaming" is involved. For one-way server push, SSE is simpler operationally and easier to test with ordinary HTTP tooling.

The sample uses a single `ScheduledExecutorService` to send ticks. A production application would usually centralize this instead of giving every controller its own scheduler, but the example keeps the moving parts visible. The completion handlers cancel the scheduled task when the emitter completes, times out, or errors:

```java
emitter.onCompletion(() -> cancel(task.get()));
emitter.onTimeout(() -> cancel(task.get()));
emitter.onError((ex) -> cancel(task.get()));
```

That matters because clients disconnect. A laptop sleeps, a browser tab closes, a mobile network changes, or a proxy cuts an idle connection. When `emitter.send(...)` fails, call `completeWithError(...)` and let cleanup run.

## Testing Async MVC

The controller slice test uses `@WebMvcTest` and `MockMvcTester`:

```java
{% include-markdown "../../code/15-async-sse-virtual-threads/maven/src/test/java/dev/springboot4docs/ch_15_async_sse_virtual_threads/JobsControllerWebMvcTest.java" comments=false %}
```

Older `MockMvc` examples often show a two-step pattern: perform the request, assert async started, then call `asyncDispatch(...)`. With `MockMvcTester`, `exchange(Duration.ofSeconds(2))` handles the wait and dispatch for an async result. The timeout is deliberately short because the sample future sleeps for only 200 ms.

The assertion checks the final response body:

```java
assertThat(result.getResponse().getContentAsString()).isEqualTo("""
		{"id":42,"status":"complete"}""");
```

That proves the test is not only seeing the first empty async response shell. It sees the completed MVC dispatch after the `CompletableFuture` has produced the `Job`.

## Testing SSE

The SSE test starts the full application on a random port and calls it with `RestTestClient`:

```java
{% include-markdown "../../code/15-async-sse-virtual-threads/maven/src/test/java/dev/springboot4docs/ch_15_async_sse_virtual_threads/EventsSpringBootTest.java" comments=false %}
```

This is intentionally an integration test. `SseEmitter` depends on real response streaming behavior, and a random-port test proves the endpoint works across the HTTP boundary instead of only inside MVC mocks.

The sample stream completes after five events, so the blocking `RestTestClient` can read the response body and assert that the first three ticks arrived:

```java
assertThat(body).contains("event:tick");
assertThat(body).contains("data:tick-1", "data:tick-2", "data:tick-3");
```

For an endless production stream, the test shape would need a client with streaming consumption and a timeout boundary. The chapter keeps the stream finite so the test remains deterministic.

The generated application test remains the basic context-load check:

```java
{% include-markdown "../../code/15-async-sse-virtual-threads/maven/src/test/java/dev/springboot4docs/ch_15_async_sse_virtual_threads/ApplicationTests.java" comments=false %}
```

Run the chapter tests from the Maven project:

```bash
./mvnw -q -B test
```

## Try It

Start the application:

```bash
./mvnw spring-boot:run
```

Call the async JSON endpoint:

```bash
curl -i http://localhost:8080/jobs/42
```

The response is still one ordinary JSON document:

```json
{"id":42,"status":"complete"}
```

The fact that MVC used async dispatch is an implementation detail of the server. The client does not see two responses. It sends one request and receives one completed response.

Call the SSE endpoint with buffering disabled so curl prints events as they arrive:

```bash
curl -N http://localhost:8080/events
```

You should see five events, roughly one second apart:

```text
event:tick
data:tick-1

event:tick
data:tick-2

event:tick
data:tick-3

```

After `tick-5`, the sample completes the emitter and the HTTP response ends. A real notification or dashboard stream might stay open for hours, with heartbeat frames keeping intermediaries from treating the connection as idle.

## Production Notes

SSE has no real backpressure protocol. If the server produces events faster than the client or network can consume them, data queues somewhere. Keep events small, avoid unbounded per-client queues, and prefer dropping or coalescing status updates over buffering forever.

Send heartbeats every 15 to 30 seconds for long-lived streams. Many load balancers, reverse proxies, and corporate networks close idle HTTP connections. A comment frame is enough for a heartbeat:

```text
: heartbeat

```

Handle disconnects as ordinary behavior. `SseEmitter.send(...)` can throw `IOException`; call `completeWithError(...)`, cancel any scheduled or subscribed work for that client, and let the next reconnect create a fresh emitter.

Keep virtual threads in perspective. They are an excellent default for many blocking MVC applications, but they are not a replacement for timeouts, connection pool sizing, bulkheads, caching, or backpressure. They make waiting cheaper; they do not make dependencies unlimited.

Chapter 16 moves from runtime behavior to API documentation with OpenAPI and Swagger UI.
