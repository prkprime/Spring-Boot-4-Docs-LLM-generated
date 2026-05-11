# Actuator

Spring Boot Actuator is the operational surface that comes with a Boot application. It answers the questions you usually need during production work: is the process healthy, what version is running, what configuration did it bind, what request mappings exist, what metrics are being recorded, and can I change a logger level without restarting the JVM?

This chapter tours the built-in endpoints, exposes a useful subset over HTTP, adds a custom health check for a background queue, adds a custom info section, and tests the important actuator responses as a running application.

## Dependencies

The sample uses Spring MVC plus Actuator. The test starter gives us `RestTestClient`, which is a good fit for checking the real HTTP actuator surface.

```xml
{% include-markdown "../../code/39-actuator/maven/pom.xml" comments=false %}
```

## What Actuator Gives You

Actuator endpoints are not application features for end users. They are operational endpoints for humans and platforms. A load balancer might call health. Kubernetes might call liveness and readiness probes. Prometheus might scrape metrics. An operator might inspect loggers, mappings, environment entries, or thread dumps while investigating an incident.

The common endpoint catalog looks like this:

- `health`: overall health and named health groups.
- `info`: selected application metadata.
- `metrics`: meter names and individual meter measurements.
- `prometheus`: metrics in Prometheus scrape format when a Prometheus registry is on the classpath.
- `env`: environment properties and property sources.
- `loggers`: configured and effective logger levels, with runtime changes.
- `beans`: Spring bean inventory.
- `configprops`: bound `@ConfigurationProperties` values.
- `mappings`: web handler mappings.
- `httpexchanges`: recent HTTP exchange history when recording is configured.
- `threaddump`: JVM thread dump.
- `heapdump`: JVM heap dump stream.
- `startup`: startup step data when startup recording is enabled.
- `scheduledtasks`: scheduled task inventory.
- `caches`: cache managers and caches.
- `sbom`: software bill of materials data when available.
- `conditions`: auto-configuration condition report.
- `shutdown`: graceful application shutdown endpoint, disabled by default.

That is a lot of power. Do not expose everything just because it is convenient in a tutorial. In production, expose the smallest set your platform and operators actually use.

## Application Configuration

This chapter intentionally exposes many endpoints so you can explore them locally:

```yaml
{% include-markdown "../../code/39-actuator/maven/src/main/resources/application.yml" comments=false %}
```

The important property is `management.endpoints.web.exposure.include`. It controls which actuator endpoints are reachable over HTTP. A common production baseline is closer to `health,info,metrics,prometheus,loggers`, and even then the administrative endpoints should live behind authentication or a private network path. Avoid `include: "*"` in production because future dependencies can add new endpoints that you did not intend to publish.

Exposure is not the same thing as existence. An endpoint must be available in the application context and exposed over the transport you are using. For example, the Prometheus endpoint also needs a Prometheus registry dependency. The `metrics` endpoint is available here because the actuator starter brings Micrometer's core instrumentation with it, so JVM, process, HTTP server, and application meters can be discovered without writing metric code in this chapter.

The health block enables Kubernetes probe groups:

```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true
```

That makes `/actuator/health/liveness` and `/actuator/health/readiness` available. Liveness answers "should the platform restart this process?" Readiness answers "should the platform send traffic here?" Those are different questions. A database outage may make a service unready without proving that the JVM needs to be killed.

The info block enables the environment-backed info contributor. Values under the top-level `info` key become part of `/actuator/info`, so this sample contributes an `app` section with a name and version.

Run the app and browse `/actuator` to see the discovery document. From there, follow a link such as `/actuator/metrics` or `/actuator/loggers/ROOT`. The discovery document is useful locally, but automation should call concrete endpoint URLs so a disabled discovery page does not break scripts.

## A Small Application Endpoint

The application has one ordinary controller:

```java
{% include-markdown "../../code/39-actuator/maven/src/main/java/dev/springboot4docs/ch_39_actuator/WidgetController.java" comments=false %}
```

It is not important as a feature. It exists so `/actuator/mappings` has an application mapping to report. That endpoint is useful when a route is not behaving the way you expect: it shows what Spring MVC actually registered, not what you think you wrote.

## Custom Health

Actuator already includes useful health contributors for common infrastructure when the matching libraries are present. You can add your own by implementing `HealthIndicator`.

```java
{% include-markdown "../../code/39-actuator/maven/src/main/java/dev/springboot4docs/ch_39_actuator/BackgroundJobQueue.java" comments=false %}
```

The queue here is deliberately simple. A real application might read a queue depth from a database table, Redis stream, broker, or scheduler service. The health indicator should keep that check cheap and bounded. Health endpoints are called frequently by platforms, so they are not a place for slow diagnostics.

```java
{% include-markdown "../../code/39-actuator/maven/src/main/java/dev/springboot4docs/ch_39_actuator/BackgroundJobHealthIndicator.java" comments=false %}
```

The bean name becomes the contributor id. `BackgroundJobHealthIndicator` is exposed as `backgroundJob` inside the health system. The implementation reads the backlog, compares it with a threshold, and returns either `Health.up()` or `Health.down()` with details.

Those details are controlled separately from the status. This chapter sets `show-details: when-authorized`, which is a safer default than always returning details to anonymous callers. A public load balancer only needs the HTTP status and top-level health status. Operators can get details through an authorized path.

Be conservative with `DOWN`. A bad health check can restart healthy processes or drain traffic from every instance at once. Use `DOWN` when the instance really should not receive traffic or cannot do useful work. Use metrics and alerts for conditions that are concerning but not process-fatal.

## Custom Info

The info endpoint is for small, stable metadata. It is not a general debugging dump. Boot can contribute values from `info.*`, Git metadata, build metadata, and custom contributors.

```java
{% include-markdown "../../code/39-actuator/maven/src/main/java/dev/springboot4docs/ch_39_actuator/BuildInfoContributor.java" comments=false %}
```

`InfoContributor` receives an `Info.Builder`. This sample adds a `build` section with the artifact name, Java version, and a fixed timestamp. In a real build, prefer generated build information from your build tool when possible, because generated metadata tracks the exact artifact that was deployed.

Good info fields include app name, version, commit, build time, image digest, and support contact. Poor info fields include secrets, full environment dumps, credentials, internal tokens, and large nested data structures. `/actuator/info` is often one of the few actuator endpoints exposed beyond the narrow admin network, so keep it boring and intentional.

## Security

If Spring Security is not on the classpath, actuator web endpoints are not protected by authentication. Once Spring Security is present, actuator endpoints require authentication unless you configure otherwise. That behavior is convenient for small demos, but production systems should make an explicit decision.

A common pattern is to run actuator on a separate port:

```yaml
management:
  server:
    port: 9090
```

Then block that port at the load balancer or ingress and allow only the platform, observability stack, and internal operators to reach it. This is often simpler than mixing user traffic and operational traffic on the same public route.

There are several footguns:

- `/actuator/env` exposes a lot of configuration. Even with sanitization, treat it as sensitive. Use `management.endpoint.env.show-values: WHEN_AUTHORIZED` when you need values available only to authenticated operators.
- `/actuator/heapdump` streams a real heap dump. Heap dumps can contain request data, credentials, tokens, and personal data.
- `/actuator/shutdown` is disabled by default. Keep it that way unless you have a strong operational reason and strict access control.
- `/actuator/loggers` can change runtime behavior. Useful during an incident, risky on an open network.

Actuator is not dangerous because it is bad. It is dangerous because it is powerful and honest about the running process.

The same thinking applies to health details. It is reasonable for an unauthenticated platform probe to know that the process is `UP` or `DOWN`. It is usually not reasonable for the public internet to see database hostnames, queue names, disk paths, or exception messages returned by health contributors. Keep public responses small and send detailed operational data through authenticated channels.

## Tests

The tests start the full application on a random port and call actuator over HTTP:

```java
{% include-markdown "../../code/39-actuator/maven/src/test/java/dev/springboot4docs/ch_39_actuator/ApplicationTests.java" comments=false %}
```

The first test checks `/actuator/health` and asserts the top-level `UP` status. That proves the actuator web endpoint is exposed and the custom health indicator is not dragging the aggregate status down.

The info test checks both sources of metadata. The `app` section comes from `info.app.*` in YAML, enabled through the environment info contributor. The `build` section comes from `BuildInfoContributor`.

The probe test calls `/actuator/health/liveness` and `/actuator/health/readiness`. These are the endpoints a Kubernetes deployment would normally wire into `livenessProbe` and `readinessProbe`.

The logger test inspects `/actuator/loggers/ROOT` and verifies that the response has `configuredLevel`. The metrics test first lists available meter names at `/actuator/metrics`, then fetches `jvm.memory.used` and checks that a measurement is present.

Run the chapter from its Maven directory:

```bash
./mvnw -q -B test
```

## What To Keep

Use Actuator as the standard operational interface for a Boot service. Expose only the endpoints you need, protect administrative endpoints, and separate management traffic when the deployment platform makes that easy. Use `HealthIndicator` for cheap, meaningful readiness and liveness signals. Use `InfoContributor` for small deployment metadata. Treat `env`, `heapdump`, `loggers`, and `shutdown` as administrative power tools.

Chapter 40 builds on this by turning the metrics side of Actuator into a fuller observability story with Micrometer metrics and distributed tracing.
