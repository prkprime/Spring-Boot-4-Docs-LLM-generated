# Logging — Structured JSON

Production logging is not "print a stack trace and hope someone reads the console." A production log stream is data. It should be easy for another system to ingest, index, filter, alert on, and join with request metadata. Plain text logs are comfortable to read locally, but they force every log platform to recover structure with regular expressions. JSON logs carry the structure from the application to the collector.

This chapter turns the console log into Elastic Common Schema JSON, adds a request correlation ID with MDC, keeps the W3C `traceparent` header visible for the observability chapter, and uses the actuator loggers endpoint to change a logger level while the app is running.

!!! note "Spring Boot 3.4+ structured logging"
    Spring Boot 3.4 introduced built-in structured logging. For this chapter, the important property is `logging.structured.format.console`. Older Boot examples often add `logback-spring.xml`, a JSON encoder dependency, and Jackson wiring. With Spring Boot 4.0.6, the common formats are built in.

## Dependencies

The sample uses Spring MVC for the endpoint, Actuator for runtime logger management, and the Spring Boot 4 MVC test support.

```xml
{% include-markdown "../../code/38-logging/maven/pom.xml" comments=false %}
```

There is no `logback-spring.xml` in this chapter. That is intentional. Custom Logback configuration is still useful when you have a specialized policy, but the default answer for ECS, Logstash, or GELF output is now a Boot property.

## Application Configuration

The whole logging setup fits in `application.yml`:

```yaml
{% include-markdown "../../code/38-logging/maven/src/main/resources/application.yml" comments=false %}
```

`spring.application.name` matters because the ECS formatter uses it as `service.name`. That field is one of the first things you filter on when several services share the same log store.

`logging.structured.format.console: ecs` tells Boot to write each console event as one JSON object using Elastic Common Schema. The other built-in format IDs are `logstash` and `gelf`. ECS is a sensible default even when you are not running Elasticsearch directly, because Datadog, Grafana Loki pipelines, OpenTelemetry collectors, and general log processors understand the same idea: stable field names beat parsing arbitrary message text.

The logger levels are ordinary Boot logging configuration. `root: INFO` keeps framework noise under control. `dev.springboot4docs: DEBUG` lets this chapter show the controller's debug log without opening the whole application. In a real service, keep production defaults conservative and raise a specific package when you are investigating a problem.

The `management.endpoints.web.exposure.include` line exposes `health`, `info`, and `loggers`. The `loggers` endpoint is the operational part of this chapter: it lets an authorized operator inspect and change logger levels at runtime. Chapter 39 goes deeper on actuator exposure and security; here we expose just enough to prove the logging workflow.

## Correlation IDs

Chapter 12 used a servlet filter to put one request ID on every response. This chapter keeps that pattern and connects it to structured logging.

```java
{% include-markdown "../../code/38-logging/maven/src/main/java/dev/springboot4docs/ch_38_logging/RequestIdFilter.java" comments=false %}
```

MDC means Mapped Diagnostic Context. It is a per-thread key/value map used by logging frameworks. When a log event is created, the encoder can copy the MDC entries into the log record. With JSON output, those entries become fields, not string fragments.

The important call is:

```java
MDC.put("requestId", requestId);
```

Every log written while the request is inside the filter chain can now include `requestId`. The `finally` block removes it. That cleanup is not optional. Servlet containers reuse threads, and MDC is commonly thread-local. If a request leaves data behind, a later request on the same worker thread can inherit the wrong correlation value.

The filter accepts an incoming `X-Request-Id` when a gateway or client already supplied one. Otherwise it generates a UUID. It also mirrors that value to the response header so a caller can report a concrete request ID when opening an incident.

The small twist is `traceparent`. W3C Trace Context uses the `traceparent` header to carry trace and span identity across services. Chapter 40 will add the observability stack that creates and exports real traces. This chapter does not need OpenTelemetry to show the shape of the correlation pattern: if a request arrives with `traceparent`, keep it in the response and put it into MDC so log search can line up with trace-aware infrastructure later.

Common MDC keys in production include `requestId`, `traceId`, `spanId`, `userId`, `tenantId`, and sometimes `jobId` for background work. Keep them low-cardinality where possible and never put secrets in MDC. MDC values are copied widely.

## Logging From Code

The controller logs the same request at three useful levels:

```java
{% include-markdown "../../code/38-logging/maven/src/main/java/dev/springboot4docs/ch_38_logging/OrderController.java" comments=false %}
```

`info` records the business event: "we are processing order 101." `debug` records a detail that is useful during investigation but too noisy for a normal production baseline. `warn` records a missing order because the request completed in a controlled way, but it may still be worth noticing.

The service writes one line at every level:

```java
{% include-markdown "../../code/38-logging/maven/src/main/java/dev/springboot4docs/ch_38_logging/LoggingDemoService.java" comments=false %}
```

This is a demo service, not a design pattern. Real code should log at the level that matches the event. `error` is for failures that need attention. `warn` is for suspicious or degraded behavior. `info` is for meaningful lifecycle or business events. `debug` and `trace` are for temporary investigation and deeper internal detail.

## What The JSON Looks Like

Start the app from the chapter directory and call the endpoint with a request ID:

```bash
./mvnw spring-boot:run
```

```bash
curl -i \
  -H 'X-Request-Id: demo-request-1' \
  -H 'traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01' \
  http://localhost:8080/orders/101
```

The console output is one JSON object per log event. A line for the controller `info` event has this shape:

```json
{"@timestamp":"2026-05-10T10:15:30.123456789Z","log":{"level":"INFO","logger":"dev.springboot4docs.ch_38_logging.OrderController"},"process":{"pid":42817,"thread":{"name":"http-nio-8080-exec-1"}},"service":{"name":"38-logging"},"message":"processing order 101","requestId":"demo-request-1","traceparent":"00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01","ecs":{"version":"8.11"}}
```

The exact timestamp, process ID, and thread name will differ, but the important fields are stable. `@timestamp` is the event time. `log.level` and `log.logger` are structured fields, not text decorations. `message` still exists for humans. `ecs.version` tells downstream tools which schema version the record follows. `service.name` comes from `spring.application.name`. The `requestId` and `traceparent` fields come from MDC.

That structure changes how you search. In Loki, you might parse JSON and filter a specific request:

```logql
{service_name="38-logging"} | json | requestId="demo-request-1"
```

In Datadog-style searches, the same idea becomes field filtering:

```text
service:38-logging @requestId:demo-request-1 @log.level:WARN
```

The exact query syntax belongs to the log platform. The application responsibility is to emit useful fields consistently.

## Runtime Logger Levels

Static logging configuration is the baseline. Runtime logging changes are for investigations. The actuator `loggers` endpoint lets you raise or lower one logger without restarting the process:

```bash
curl -i -X POST \
  -H 'Content-Type: application/json' \
  -d '{"configuredLevel":"WARN"}' \
  http://localhost:8080/actuator/loggers/dev.springboot4docs
```

Then inspect it:

```bash
curl http://localhost:8080/actuator/loggers/dev.springboot4docs
```

You should see `configuredLevel` and `effectiveLevel` as `WARN`. The difference between those fields matters. `configuredLevel` is the level set directly on that logger. `effectiveLevel` is the level that actually applies after inheritance. A child logger with no configured level inherits from its nearest configured parent.

This is useful in production when one endpoint is misbehaving and you need more detail for one package. It is also dangerous if exposed casually. Treat `/actuator/loggers` as an administrative endpoint. Do not put it on the public internet, and do not leave broad debug logging enabled after the investigation.

## Sensitive Data

Structured logging makes sensitive data easier to find, but it also makes accidental leaks easier to index forever. The first rule is still the right one: do not log secrets in the first place. Do not log passwords, bearer tokens, API keys, session cookies, private keys, or full payment data.

Masking can be a backup control. In Logback-heavy systems you may see a `MaskingPatternLayout`. In JSON serialization paths, a Jackson mix-in or a custom redaction layer can exclude fields. Those tools are useful at boundaries, but they are not a license to log raw request bodies. Prefer small, deliberate log messages with identifiers that let you find the record of interest without storing the secret itself.

## Tests

The MVC slice test proves the filter behavior while the request is still inside the filter chain:

```java
{% include-markdown "../../code/38-logging/maven/src/test/java/dev/springboot4docs/ch_38_logging/RequestIdFilterWebMvcTest.java" comments=false %}
```

The test sends `X-Request-Id` and `traceparent`. A test-only probe filter runs immediately after `RequestIdFilter` and reads MDC before cleanup happens. After the request completes, the assertions check the response headers and verify that MDC was cleaned from the test thread.

The integration test starts the full application and drives the actuator endpoint over HTTP:

```java
{% include-markdown "../../code/38-logging/maven/src/test/java/dev/springboot4docs/ch_38_logging/LoggerEndpointIntegrationTest.java" comments=false %}
```

The `POST` changes the package logger to `WARN`. The following `GET` verifies both the configured and effective level. This is the same flow an operator would use during a production investigation, usually through an internal admin route with authentication in front of it.

Run the chapter from its Maven directory:

```bash
./mvnw -q -B test
```

## What To Keep

Use Spring Boot's built-in structured logging before reaching for custom Logback XML. Prefer ECS unless your log platform strongly prefers Logstash or GELF. Put request correlation values in MDC, clean them up in `finally`, and keep trace headers visible so logs can line up with traces later. Expose runtime logger changes only through a protected actuator surface.

Chapter 39 stays with Actuator and turns from log-level management to production endpoints such as health, readiness, liveness, info, metrics discovery, and endpoint exposure policy.
