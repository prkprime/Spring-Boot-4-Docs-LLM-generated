# Dev Loop

## What we'll build

In this chapter we add a tiny endpoint that returns the current time, then use it to practice the development loop: edit, compile, restart, poke.

That loop is the everyday rhythm of application work. You edit a Java file, compile the changed code, restart the running application, and poke the app with a browser, `curl`, or a test. The shorter that loop is, the easier it is to stay focused. When the loop is slow, you spend more time managing the process than thinking about the feature.

Without Spring Boot DevTools, every code change needs a manual restart. You stop the running process, run `./mvnw spring-boot:run` again, wait for Spring Boot to start, and then call the endpoint again. That is fine for the first hello-world app, but it gets old quickly.

DevTools removes most of that friction during local development.

!!! note "Spring Boot 4 only"
    This chapter uses the Spring Boot 4 MVC dependency names: `spring-boot-starter-webmvc` for the application and `spring-boot-starter-webmvc-test` for the web MVC test slice. If you are used to Spring Boot 3, the split starter names are one of the visible changes.

## The endpoint

The controller for this chapter is intentionally small:

```java
{% include-markdown "../../code/02-dev-loop/maven/src/main/java/dev/springboot4docs/ch_02_dev_loop/TimeController.java" comments=false %}
```

`@RestController` tells Spring MVC that return values from this class should be written to the HTTP response body. `@GetMapping("/now")` maps `GET /now` to the `now()` method.

The method returns a Java record:

```java
record TimeResponse(Instant now) {
}
```

A record is a compact way to describe an immutable response shape. The single component, `Instant now`, becomes a JSON property named `now`. Jackson is already on the classpath through the web starter, so Spring MVC can serialize the record without extra configuration.

The response will look like this:

```json
{"now":"2026-05-09T10:15:30.123456Z"}
```

The exact value changes on every request because the controller calls `Instant.now()`.

## The DevTools dependency

This project already includes Spring Boot DevTools:

```xml
{% include-markdown "../../code/02-dev-loop/maven/pom.xml" start="<!-- docs:devtools:start -->" end="<!-- docs:devtools:end -->" comments=false %}
```

DevTools is a local-development helper. Its most visible feature is automatic restart: when compiled classpath files change, DevTools restarts the application context for you. In practice, that means you edit a controller, recompile it, and watch the app restart without manually killing and rerunning the Maven command.

DevTools also starts an automatic LiveReload server. LiveReload is a tiny TCP server on port `35729`. If you install the LiveReload browser extension, the extension can connect to that server and refresh the browser when application resources change. It is most useful once you have templates, static files, or pages open in a browser.

DevTools also applies sensible development defaults. A common example is disabling template caching so server-rendered pages are easier to iterate on. Production builds should keep production-oriented caching and startup behavior; DevTools is meant for the machine where you are actively editing code.

The dependency has two important flags:

```xml
<scope>runtime</scope>
<optional>true</optional>
```

`runtime` keeps DevTools out of compile-time application code. Your controllers and services should not call DevTools APIs.

`optional=true` matters when another project depends on this application artifact. It tells Maven not to drag DevTools transitively into downstream consumers. That keeps a local convenience dependency from leaking into places where it does not belong, especially production packaging paths.

!!! note "Development only"
    DevTools is for the local edit/run cycle. It is not an operations restart feature, and this chapter does not use an HTTP restart endpoint. Keep the mental model simple: edit code, compile code, let DevTools restart the local process.

## How restart is triggered

DevTools watches the application classpath, not your editor buffer. Saving a Java file is only half of the story. The changed Java file must be compiled into `target/classes` before DevTools has something to restart from.

In an IDE, the usual trigger is project compilation.

In IntelliJ IDEA, enable automatic builds for the project and use the IDE's auto-restart-on-update support for Spring Boot runs. Then saving or building the project causes changed classes to land on the classpath, and DevTools restarts the app.

In VS Code, the Spring Boot Dashboard provides a similar development mode for running Boot applications with restart and live-reload support. The important idea is the same: the editor or IDE has to compile the changed Java file while the app is still running.

Terminal-only readers can use the Maven command directly:

```bash
cd code/02-dev-loop/maven
./mvnw spring-boot:run
```

Leave that terminal open. Edit a source file, save it, and let your build tool or IDE compile the change. When DevTools sees the classpath update, the log rolls through a restart.

If you are using only a terminal and no background compiler, you may still need to stop and rerun `./mvnw spring-boot:run` after editing Java code. DevTools shortens the restart once class files change; it does not turn a plain text editor into a Java compiler.

## Quick demo

Start the app:

```bash
cd code/02-dev-loop/maven
./mvnw spring-boot:run
```

In another terminal, call the endpoint:

```bash
curl -s localhost:8080/now
```

You should receive JSON with a `now` field:

```json
{"now":"2026-05-09T10:15:30.123456Z"}
```

Now change the controller. For example, temporarily rename the record component from `now` to `serverTime`, save the file, and compile the project from your IDE. Watch the `spring-boot:run` terminal. You should see the application restart.

Then call the endpoint again:

```bash
curl -s localhost:8080/now
```

That is the dev loop: edit, compile, restart, poke. DevTools makes the restart step automatic once the classpath changes.

## LiveReload

LiveReload solves a different part of the loop. DevTools runs a LiveReload server on port `35729`. A browser extension connects to that local server and refreshes the page when it receives a change signal.

For a JSON endpoint like `/now`, LiveReload is not very exciting because you are probably using `curl`. Later, when the app serves HTML templates or static assets, it becomes more useful: keep the page open, change a template or CSS file, save, and let the browser refresh itself.

Only one LiveReload server can use port `35729` at a time. If you run several Boot apps with DevTools, the first one usually wins that port.

## The test

The endpoint test uses a focused MVC slice:

```java
{% include-markdown "../../code/02-dev-loop/maven/src/test/java/dev/springboot4docs/ch_02_dev_loop/TimeControllerTest.java" comments=false %}
```

`@WebMvcTest(TimeController.class)` starts only the Spring MVC slice needed for `TimeController`. It does not start the full application or bind to port `8080`.

`MockMvcTester` sends a mock `GET /now` request through Spring MVC's test infrastructure. The assertions verify two things: the response has status `200 OK`, and the JSON body contains a `now` field.

The test does not assert the exact timestamp. That would be brittle because `Instant.now()` changes every time the endpoint runs. For this chapter, the contract is the response shape: successful JSON with a `now` property.

## Run it

From the project directory, run the full test suite:

```bash
cd code/02-dev-loop/maven
./mvnw -q -B test
```

Then start the application:

```bash
./mvnw spring-boot:run
```

Leave it running and call the endpoint from another terminal:

```bash
curl -s localhost:8080/now
```

Make a small controller edit, save, compile from your IDE, and watch the restart log roll. Call `/now` again to confirm the running app is using the latest code.
