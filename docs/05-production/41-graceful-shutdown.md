# Graceful Shutdown

Production processes rarely stop because a person carefully calls a method. They stop because Kubernetes replaces a pod, systemd restarts a unit, a VM is drained, a deploy script sends `SIGTERM`, or you press `Ctrl+C` in a terminal. The useful question is what happens to requests that are already inside the process when that signal arrives.

This chapter enables Spring Boot's graceful shutdown support, adds a slow endpoint that makes shutdown behavior visible, and adds two shutdown hooks so you can see where ordinary bean cleanup fits.

## Dependencies

The sample uses Spring MVC for the request, Actuator for the shutdown endpoint, and the Spring Boot MVC test support for HTTP tests.

```xml
{% include-markdown "../../code/41-graceful-shutdown/maven/pom.xml" comments=false %}
```

## Default vs Graceful Shutdown

Without graceful shutdown, the web server stops as part of application shutdown and in-flight work can be cut short. That is fine for a local demo, but it is a poor fit for production traffic. A client might already have sent a payment request, an admin action, or a write that is halfway through a transaction. Killing the request immediately makes deploys and restarts look like random application failures.

Graceful shutdown changes the web server part of the sequence. When the application context begins closing, the server stops accepting new requests and gives in-flight requests time to finish. The time budget comes from `spring.lifecycle.timeout-per-shutdown-phase`. When the budget expires, remaining work is forcibly closed so the process can exit.

That budget matters outside the JVM. Kubernetes defaults `terminationGracePeriodSeconds` to 30 seconds. If your Boot shutdown phase timeout is also 30 seconds, the platform and the application are racing each other. Set the application budget a little lower than the platform budget, for example 25 seconds inside a pod with a 30 second termination grace period.

## Application Configuration

The shutdown behavior is configured in `application.yml`:

```yaml
{% include-markdown "../../code/41-graceful-shutdown/maven/src/main/resources/application.yml" comments=false %}
```

`server.shutdown: graceful` is the main switch. It tells Boot to ask the embedded web server to drain instead of closing active exchanges immediately.

`spring.lifecycle.timeout-per-shutdown-phase: 25s` sets the maximum time to wait for each lifecycle phase. Shutdown is not one global unordered callback list. Spring stops `SmartLifecycle` beans by phase. Higher phase numbers stop first; lower phase numbers stop later. The embedded web server participates in that model at `SmartLifecycle.DEFAULT_PHASE - 1024`, which means most default-phase lifecycle beans stop before the web server phase.

The actuator block exposes `health`, `info`, and `shutdown`, then explicitly enables the shutdown endpoint. `/actuator/shutdown` is disabled by default for a good reason: it can stop the process. In a real system, enable it only behind authentication and preferably on a separate management port that is reachable by operators or automation, not by public clients.

## A Slow Request

The controller gives us one endpoint that stays in flight long enough to observe:

```java
{% include-markdown "../../code/41-graceful-shutdown/maven/src/main/java/dev/springboot4docs/ch_41_graceful_shutdown/SlowController.java" comments=false %}
```

`GET /work?seconds=5` sleeps for five seconds and then returns `OK`. The value is clamped to a small range so a typo does not leave a local request sleeping for minutes.

This endpoint is deliberately boring. Graceful shutdown is easiest to understand when the request has one obvious behavior: enter, wait, return. With graceful shutdown enabled, a termination signal that arrives during the sleep should stop new requests from being accepted while allowing the sleeping request to finish, as long as it completes before the shutdown phase timeout.

Scheduled tasks fit the same operational story even though they are not HTTP requests. When the application context closes, schedulers and lifecycle-managed infrastructure are stopped as part of the same shutdown sequence. Long-running scheduled work should be designed with the same budget in mind: finish quickly, observe interruption where appropriate, and avoid starting fresh expensive work once shutdown has begun. Graceful shutdown is a drain window, not an unlimited background-job checkpointing system.

## Shutdown Hooks

Spring gives you several levels of shutdown participation. For ordinary bean cleanup, `@PreDestroy` is usually enough:

```java
{% include-markdown "../../code/41-graceful-shutdown/maven/src/main/java/dev/springboot4docs/ch_41_graceful_shutdown/PreDestroyCleanup.java" comments=false %}
```

Use this for local cleanup tied to one bean: close a client, flush a small buffer, release a resource, or write a final log line. Keep it bounded. A `@PreDestroy` method that blocks for a long time consumes the same shutdown budget your server and other beans need.

For ordering, use `SmartLifecycle`:

```java
{% include-markdown "../../code/41-graceful-shutdown/maven/src/main/java/dev/springboot4docs/ch_41_graceful_shutdown/ShutdownPhaseLogger.java" comments=false %}
```

`SmartLifecycle.getPhase()` controls order. Higher phase numbers stop first. This sample uses `SmartLifecycle.DEFAULT_PHASE`, so it logs before the embedded web server's graceful shutdown phase. In a real app, a message listener might use a high phase to stop taking new broker messages early, while a database pool or shared client might use a lower phase so it remains available while business components finish.

Prefer `stop(Runnable callback)` for lifecycle beans. The callback tells Spring that the bean has completed its stop work. If you do asynchronous cleanup, call the callback after the async work is finished. If the callback is never called, shutdown waits until the phase timeout expires.

## Kubernetes Flow

In Kubernetes, a normal pod termination looks like this:

1. The pod receives `SIGTERM`.
2. Spring begins closing the application context.
3. Boot's graceful web server shutdown stops accepting new requests and waits for in-flight requests.
4. Spring lifecycle callbacks run, including `SmartLifecycle.stop()` and destruction callbacks such as `@PreDestroy`.
5. Readiness becomes false as the process is leaving service, so the platform stops routing new traffic to the pod.
6. If the process has not exited before `terminationGracePeriodSeconds`, Kubernetes sends `SIGKILL`.

The pod spec needs a grace period that is long enough for the application budget:

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: graceful-demo
spec:
  terminationGracePeriodSeconds: 30
  containers:
    - name: app
      image: example/graceful-demo:latest
```

A `preStop` hook can be useful when your load balancer or service mesh needs a moment to remove the instance from rotation before the JVM receives `SIGTERM`. Keep that hook short and count it against the same Kubernetes grace period. If `preStop` sleeps for 10 seconds and the pod grace period is 30 seconds, the JVM no longer has the full 30 seconds.

## Tests

The integration tests start the application on a random port:

```java
{% include-markdown "../../code/41-graceful-shutdown/maven/src/test/java/dev/springboot4docs/ch_41_graceful_shutdown/ApplicationTests.java" comments=false %}
```

The first test reads the `Environment` and asserts that `server.shutdown` is set to `graceful`. This is a small configuration test, but it protects the main behavior of the chapter from an accidental YAML edit.

The second test calls `POST /actuator/shutdown` and expects `200 OK`. Actuator shutdown closes the application context, so the test also waits briefly for the `ShutdownPhaseLogger` bean to record that its `stop` method ran. `@DirtiesContext` tells the Spring test framework that this test intentionally shut down the context.

This endpoint is enabled here because the chapter needs an automated shutdown path. That is not a production recommendation. In production, the normal shutdown path is usually the platform sending `SIGTERM`, not an HTTP endpoint exposed to application users.

Run the chapter from its Maven directory:

```bash
./mvnw -q -B test
```

## Run It

Start the application:

```bash
./mvnw spring-boot:run
```

In another terminal, start a slow request:

```bash
curl -i "http://localhost:8080/work?seconds=5"
```

Before the request returns, press `Ctrl+C` in the terminal running the application. The server begins graceful shutdown. The slow request should still finish and return `OK` before the JVM exits, provided it finishes inside the configured 25 second phase timeout.

Then try sending another request while shutdown is underway. The server is already draining, so new work should not be accepted. That is the point: graceful shutdown protects work that is already inside the process; it is not a way to keep serving fresh traffic during termination.

## What To Keep

Enable `server.shutdown: graceful` for services that receive real traffic. Set `spring.lifecycle.timeout-per-shutdown-phase` to a value that fits inside the platform's termination grace period. Remember that `SmartLifecycle` phases define stop order, with higher phases stopping first, and use `@PreDestroy` for simple per-bean cleanup. Treat `/actuator/shutdown` as an administrative endpoint and keep it protected when you enable it at all.

Chapter 42 turns back to testing and builds a broader set of slice tests for production code.
