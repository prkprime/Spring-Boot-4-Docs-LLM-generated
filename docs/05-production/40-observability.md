# Observability - Metrics & Traces

Observability is usually described with three pillars: metrics, traces, and logs. The point is not to collect three piles of data. The point is to make a production system answer better questions.

Each pillar has a different access pattern. Metrics are aggregated numbers over time. They are cheap to query, good for dashboards and alerts, and usually the first signal that something changed. Traces follow one request or job through the system. They are useful when you need to see where time was spent or which downstream call failed. Logs are event records. They carry the human-readable facts that explain what the code decided to do.

Chapter 38 added structured logs and request correlation. Chapter 39 exposed Actuator endpoints. This chapter connects those ideas to Micrometer metrics, Prometheus scraping, and OpenTelemetry tracing.

## Dependencies

The sample uses Spring MVC, Actuator, Prometheus registry support, Micrometer tracing with the OpenTelemetry bridge, and an OTLP exporter. Spring Boot manages the Micrometer and OpenTelemetry versions. The direct Spring AOP and AspectJ dependencies make the `@Observed` aspect active.

```xml
{% include-markdown "../../code/40-observability/maven/pom.xml" comments=false %}
```

Micrometer is the metrics facade used by Spring Boot. Since Micrometer 1.13, the same instrumentation model has also been the natural entry point for observations that can produce both metrics and tracing spans. The registry decides where metrics go. In this chapter, metrics go to Prometheus through `/actuator/prometheus`, but the same application code can bridge to Datadog, CloudWatch, Graphite, OTLP metrics, and other monitoring systems by changing dependencies and configuration.

That facade boundary is important. Application code should describe the thing it measured: an order was processed, a payment call took 120 ms, or a queue currently has 42 items waiting. It should not know the storage model of the monitoring backend. Prometheus, Datadog, and CloudWatch have different query languages and operational tradeoffs, but the instrumentation in `OrderService` stays ordinary Micrometer code.

## Application Configuration

The configuration exposes a small actuator surface, adds a common application tag to every metric, enables full trace sampling for local development, and points OTLP tracing at a local collector endpoint by default.

```yaml
{% include-markdown "../../code/40-observability/maven/src/main/resources/application.yml" comments=false %}
```

`management.endpoints.web.exposure.include` exposes `health`, `info`, `metrics`, and `prometheus`. Prometheus does not call the JSON metrics endpoint. It scrapes `/actuator/prometheus`, which is available because `micrometer-registry-prometheus` is on the classpath.

The `management.metrics.tags.application` value is a common tag. Every meter gets `application=40-observability`, which makes dashboards and alerts easier to filter when several services share the same Prometheus server. Common tags should be stable and low-cardinality: application, region, environment, and instance are typical. Do not tag metrics by user id, email address, request id, session id, raw URL, or anything else that can grow without a strict bound.

Cardinality is the most common way teams make metrics expensive. A counter named `orders.processed.total` with `outcome=success|failure` creates two time series per application and instance. The same counter tagged with `userId` creates one time series per user. Add `sku`, `campaign`, and `requestId`, and the backend spends more time storing labels than answering useful questions. Prefer logs or traces for high-cardinality facts, and keep metric tags for dimensions you would actually graph or alert on.

The tracing block sets `management.tracing.sampling.probability: 1.0`. That means "sample every trace" and is convenient while learning or running locally. In production, probability sampling is often closer to 1-5% at the application. A larger platform may do tail-based sampling in an OpenTelemetry Collector, where the collector keeps traces after it sees an error, slow path, or important route. Source sampling is cheap and simple. Tail sampling can be smarter, but it needs collector infrastructure.

The OTLP endpoint uses an environment variable pattern:

```yaml
endpoint: ${OTLP_TRACES_ENDPOINT:http://localhost:4318/v1/traces}
```

That gives local development a working default while letting a deployment set `OTLP_TRACES_ENDPOINT` without rebuilding the application. In production, a common shape is to run an OpenTelemetry Collector as a sidecar or node-local agent and have the app export OTLP to `localhost`.

The logging pattern is the bridge back to chapter 38:

```yaml
level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
```

Micrometer tracing puts the current `traceId` and `spanId` in MDC while request handling is active. The pattern copies those values into text logs. In JSON logging, the same values can be emitted as fields. Either way, the log line can be joined back to the trace.

## Metric Types

Micrometer gives you a small set of metric types. Use the simplest one that matches the question you need to answer.

- `Counter`: a number that only goes up, such as processed orders or failed payments.
- `Gauge`: a sampled value, such as queue depth or active connections.
- `Timer`: count plus duration statistics for short operations.
- `DistributionSummary`: distribution of non-time values, such as payload size.
- `LongTaskTimer`: duration for tasks that are still running, such as a batch job.

Counters are good for rates: orders per minute, errors per second, retries per hour. Timers are good for latency. Gauges are useful but easy to misuse because the value is sampled when the registry observes it. A gauge should point at a real current state, not a value you manually increment and decrement when a counter or timer would be clearer.

## The Order Endpoint

The application endpoint is intentionally small. It gives the chapter one business action to instrument.

```java
{% include-markdown "../../code/40-observability/maven/src/main/java/dev/springboot4docs/ch_40_observability/Order.java" comments=false %}
```

```java
{% include-markdown "../../code/40-observability/maven/src/main/java/dev/springboot4docs/ch_40_observability/OrderReceipt.java" comments=false %}
```

```java
{% include-markdown "../../code/40-observability/maven/src/main/java/dev/springboot4docs/ch_40_observability/OrderController.java" comments=false %}
```

`POST /orders` accepts an order, calls the service, and returns `201 Created`. Spring MVC server instrumentation already records HTTP metrics and observations for the request. The service adds business-level instrumentation, which is usually where the most useful custom meters live.

## Custom Metrics and Observations

`OrderService` records two custom signals. It increments a counter named `orders.processed.total` with an `outcome` tag, and it records processing time in a timer named `orders.process.duration`.

```java
{% include-markdown "../../code/40-observability/maven/src/main/java/dev/springboot4docs/ch_40_observability/OrderService.java" comments=false %}
```

The counter is tagged with `outcome=success` or `outcome=failure`. That is a bounded tag: there are only two values. It lets Prometheus query success and failure rates separately without creating a new time series for every customer, SKU, or request.

The timer wraps the operation with `Timer.record(...)`. Timers publish a count and total time, and registries can add max, histogram, or percentile behavior depending on configuration. You should usually time a boundary that has operational meaning: a database call, a remote API call, an order processing step, or an entire request handler. Timing tiny internal methods creates noise and makes dashboards harder to read.

The method also has `@Observed(name = "order.process")`. The Micrometer Observation API is the modern unified entry point for metrics and traces. One observation can become a timer-style metric and a tracing span when tracing is configured. It replaces the older habit of putting `@Timed` on methods when you only wanted latency metrics. `@Timed` still appears in older examples, but `@Observed` is the better default for new Spring Boot applications because it keeps metrics and traces aligned.

There is one important trap: `@Observed` needs an aspect. Without `ObservedAspect`, the annotation is just metadata and the method is not intercepted.

```java
{% include-markdown "../../code/40-observability/maven/src/main/java/dev/springboot4docs/ch_40_observability/MetricsConfig.java" comments=false %}
```

The aspect receives the application `ObservationRegistry`. Boot auto-configures that registry and wires the registered observation handlers. With the OpenTelemetry bridge on the classpath, observations can create spans that are exported through the configured OTLP endpoint.

## OpenTelemetry Tracing

OpenTelemetry is the vendor-neutral model for traces, metrics, and logs. In this chapter, Spring Boot uses Micrometer tracing as the application facade and bridges it to OpenTelemetry with `micrometer-tracing-bridge-otel`. The exporter dependency, `opentelemetry-exporter-otlp`, gives the app a way to send spans to a collector.

The application does not import OpenTelemetry APIs directly. That is deliberate. Most Spring applications should instrument application code with Micrometer observations and let Boot configure the tracing backend. This keeps business code free of vendor-specific tracing calls.

Trace context propagation is what makes a distributed trace distributed. Spring uses W3C Trace Context by default, carried in the `traceparent` HTTP header. When service A calls service B, the current trace id and parent span information are sent in that header. Service B continues the same trace with a new span. When logs are written during either request, MDC contains the current `traceId` and `spanId`, so logs and traces share the same identifiers.

That gives you a practical debugging path: an alert fires from a metric, you open a slow or failing trace for the affected route, and then you filter logs by `traceId` to read the application events for that exact request.

Propagation should happen through framework clients wherever possible. Spring MVC server handling, supported HTTP clients, messaging libraries, and task execution integrations know how to carry context without hand-copying headers through business methods. If you create raw threads, custom executors, or low-level HTTP calls, verify that observation context still crosses that boundary. Missing context usually shows up as broken traces: one request becomes several unrelated root spans.

## Tests

The tests run the full application on a random port. They call the real actuator endpoints rather than inspecting controller methods directly.

```java
{% include-markdown "../../code/40-observability/maven/src/test/java/dev/springboot4docs/ch_40_observability/ApplicationTests.java" comments=false %}
```

`@AutoConfigureMetrics` matters in this test. Spring Boot's metrics test support can disable metrics export to keep ordinary tests quiet and cheap. This chapter explicitly tests the Prometheus scrape endpoint, so the test opts export back in and verifies the same endpoint a scraper would call.

The Prometheus test posts one order first, because a custom meter only appears after code records it. Then it fetches `/actuator/prometheus` as body text and checks for the Prometheus meter name `orders_processed_total` and the `outcome="success"` label. Prometheus uses underscores in the scrape format even though the Micrometer meter name is dotted. The test does not assume label order because common tags such as `application` can appear before business tags.

The registry test uses the autowired `MeterRegistry` and checks the counter before and after a `POST /orders`. This is a direct assertion that the business operation increments the meter, independent of the Prometheus endpoint.

The JSON actuator test calls `/actuator/metrics/orders.processed.total` and checks that a measurement exists. That endpoint is useful for local inspection and tests, but Prometheus should scrape `/actuator/prometheus` in production.

Run the chapter from its Maven directory:

```bash
./mvnw -q -B test
```

## Production Notes

Keep metric cardinality under active control. A single counter with a tag that has 100,000 possible values is not a single cheap metric anymore; it is 100,000 time series. Set a cardinality budget, review new tags during code review, and alarm on unexpected meter growth in the monitoring backend.

Run an OpenTelemetry Collector close to the application. A sidecar, daemonset, or node-local collector lets apps export OTLP locally and lets the collector handle batching, retries, authentication, filtering, and vendor export. Applications should not need to know every backend destination.

Use 100% trace sampling in development and small environments where traffic is low. In production, start with a probability that your storage and query tools can afford, commonly 1-5%, and increase sampling for important routes or errors at the collector when you need more detail.

Keep logs, metrics, and traces connected by shared names and identifiers. Route names, outcome tags, exception names, `traceId`, and `spanId` should mean the same thing across the stack. Observability breaks down when every signal uses different vocabulary.

Chapter 41 continues the production track with graceful shutdown, which is another operational behavior that shows up clearly in metrics, traces, and logs when the application is instrumented well.
