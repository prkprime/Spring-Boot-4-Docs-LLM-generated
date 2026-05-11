# Spring Boot 4 + Spring Framework 7: What Changed

Spring Boot 4 is not a different framework. It is still Spring Boot: dependency injection, auto-configuration, externalized configuration, starters, tests, actuator endpoints, data repositories, security filters, and the same basic application shape you have already used in the first three chapters.

What changed is the foundation underneath that familiar shape. Spring Boot 4 sits on Spring Framework 7, Jakarta EE 11, JUnit 6, Jackson 3, Mockito 5.x, and the current generation of Java runtime features. That combination changes enough names, defaults, dependencies, and test APIs that older tutorials can now be actively misleading.

This chapter is a map. We are not going to build anything here. The goal is to make the rest of the guide feel honest: when something is normal Spring, we will say so; when something is new in Spring Boot 4 or Spring Framework 7, we will call it out.

## Versioning

Spring Boot 4 means Spring Framework 7. That is the biggest version relationship to keep in your head. Boot provides the curated dependency management and auto-configuration layer; Framework provides the core container, web stack, test support, resource handling, validation integration, and many of the APIs you touch directly.

The baseline stack for this guide is Spring Boot 4, Spring Framework 7, Jakarta EE 11, JUnit 6, Jackson 3, and Mockito 5.x. When this guide was written, the latest Spring Boot 4 patch release was 4.0.6.

Patch versions matter less than the major line, but they are not irrelevant. If your generated project uses `4.0.x`, you are in the right family. If it says `3.5.x`, you are reading the previous generation.

!!! warning "Beware older articles"
    Many search results still describe Spring Boot 2 or 3. They may teach useful Spring concepts, but they can be wrong about package names, starters, test dependencies, Java expectations, and library versions.

## Java Baseline: The Truth

Spring Boot 4 requires Java 17 or later. That is the minimum. A project running on Java 17 is not automatically obsolete just because Spring Boot 4 has arrived.

For this guide, we target Java 25 LTS. That gives us the current long-term-support runtime, modern JVM behavior, and a clean place to discuss virtual threads without treating them as an exotic preview feature. Java 21 also works fine for Spring Boot 4 applications.

The important distinction is minimum versus recommended. Spring Boot 4 does not require Java 25. We recommend Java 25 for these docs because it is a good current target, not because the framework refuses to run on anything lower.

!!! info "Truth"
    Articles that say "Spring Boot 4 requires Java 25" are wrong. The minimum is Java 17. This guide uses Java 25 LTS as its sample target.

## Jakarta EE 11

Spring Boot 4 aligns with Jakarta EE 11. In practical web application terms, that means Servlet 6.1, JPA 3.2, and Bean Validation 3.1 are part of the platform level you should expect.

The package namespace is fully `jakarta.*`. That was already true in Spring Boot 3, but it is worth repeating because a huge amount of old Spring content still imports `javax.persistence`, `javax.validation`, or `javax.servlet`.

If you are copying from an older article and an import starts with `javax`, stop and translate it before assuming your project is broken. In a Spring Boot 4 application, the modern Jakarta namespace is the expected one.

!!! warning "Beware older articles"
    `javax.*` examples usually come from Spring Boot 2-era material. Spring Boot 4 is firmly in the `jakarta.*` world.

## Modularized Starters and Jars

One of the easiest Spring Boot 4 changes to trip over is the web starter split. The old general-purpose `spring-boot-starter-web` dependency is no longer the name to reach for in this guide. Servlet-style Spring MVC applications use `spring-boot-starter-webmvc`; reactive WebFlux applications use `spring-boot-starter-webflux`.

The test side split as well. For servlet MVC tests, use `spring-boot-starter-webmvc-test`. For reactive WebFlux tests, use `spring-boot-starter-webflux-test`. You already saw the servlet path in the chapter 1 `pom.xml`: this guide deliberately starts with WebMVC so the dependency names are explicit from the beginning.

Package names moved with that modularization. In particular, MVC test auto-configuration now lives under `webmvc.test.autoconfigure`, replacing the older `web.servlet.autoconfigure` shape you may see in Spring Boot 3 material.

!!! warning "Beware older articles"
    This is a real migration footgun. If an article tells you to add `spring-boot-starter-web` or imports test auto-configuration from `web.servlet.autoconfigure`, assume it was written for an older Boot line and verify every dependency name.

## JSpecify Nullability

Spring Framework 7 uses JSpecify annotations for nullability metadata. You will see framework APIs described with `org.jspecify.annotations.Nullable` and `org.jspecify.annotations.NonNull`.

For Java users, this mostly improves editor and build-tool feedback. Your IDE can warn more accurately when you pass or return `null` in places the framework marks as non-null. For Kotlin users, the same metadata helps Kotlin understand Spring APIs with fewer ambiguous platform types.

This is not a new programming model. You still write normal Spring components. The difference is that the framework's contracts are clearer to tools.

## Jackson 3

Spring Boot 4 moves to Jackson 3. That is a major version bump for the JSON library Spring Boot uses by default for HTTP request and response bodies.

For typical controller DTOs, the day-to-day experience is still familiar: records, classes, fields, accessors, and Jackson annotations all remain part of the model. Records are first-class citizens, which fits the style we will use throughout the guide for request and response shapes.

The stricter parts matter when your JSON model gets more advanced. Polymorphic deserialization is tighter, and constructor binding with `@JsonProperty` is more reliable. Those are good changes, but they can expose assumptions in older code.

We will exercise the practical side of this in chapter 7, when our API starts accepting and returning more realistic data shapes.

## JUnit 6

Spring Boot 4's test stack uses JUnit 6. Most application tests still look almost exactly like JUnit 5 tests, especially if you are writing straightforward Spring Boot integration tests.

There are minor breaking changes around the platform and extension ecosystem, so older custom extensions or build plugins may need attention. For normal test classes, the annotations you expect are still there.

We will use familiar JUnit features such as `@Nested` and `@DisplayName` later in the guide. The point is not that JUnit 6 changes how you think about tests; it updates the test platform underneath Spring Boot's current generation.

!!! warning "Beware older articles"
    JUnit 5 examples often still translate cleanly, but dependency coordinates and platform versions may not. Let Spring Boot 4 manage the test stack instead of pinning old JUnit versions yourself.

## MockMvcTester

!!! note "Spring Boot 4 only"
    `MockMvcTester` is new in Spring Framework 7, which means it is part of the Spring Boot 4 generation. You already used it in chapter 1.

`MockMvcTester` is the modern fluent testing API for servlet MVC controllers. It gives you an AssertJ-style way to test MVC requests and responses without the older `MockMvc.perform(...).andExpect(...)` chain dominating every test.

This does not make `MockMvc` disappear. It gives servlet MVC tests a cleaner default surface for new code. If you have older tests using `MockMvc`, the concepts still transfer: send a request, inspect the response, assert the status, headers, and body.

The reason we introduced it immediately in chapter 1 is simple: if you are learning Spring Boot 4 from scratch, this is the test style you should recognize first.

## RestTestClient

!!! note "Spring Boot 4 only"
    `RestTestClient` is new in Spring Framework 7. We will use it in chapter 5 when we move from slice-style MVC testing to full application integration tests.

`RestTestClient` is the modern blocking integration-test client for Spring applications. It is designed for tests that start the application and make real HTTP calls into it.

In older Spring Boot material, you will often see `TestRestTemplate` used for this role. That type still explains the old pattern, but for new Spring Boot 4 code, `RestTestClient` is the API to learn first.

The distinction matters because chapter 5 will test the running application, not just the controller layer. That is where a client like this becomes useful.

## @MockitoBean

!!! note "Spring Boot 4 only"
    `@MockitoBean` is the new Spring test annotation for Mockito-backed beans. We will use it in chapter 5.

Spring Boot's older `@MockBean` annotation is deprecated in favor of `@MockitoBean`. The job is familiar: replace or provide a bean in the application context with a Mockito mock so a Spring test can isolate the thing under test.

The new name is more explicit. It also fits with the broader cleanup in Spring's test support. If you see `@MockBean` in older examples, read it as a clue that the article predates the current testing API.

!!! warning "Beware older articles"
    `@MockBean` examples are common and often conceptually useful, but new Spring Boot 4 tests should use `@MockitoBean`.

## API Versioning Auto-Configuration

!!! note "Spring Boot 4 only"
    API versioning auto-configuration is new in Spring Boot 4. We will use it in chapter 10.

Spring Boot 4 adds first-class auto-configuration for API versioning strategies. Instead of every project inventing its own convention from scratch, Boot can help wire common approaches such as version headers, versioned paths, and media-type based versioning.

This matters once an API has real clients. Versioning is not just a route-matching trick; it affects documentation, compatibility promises, deprecation plans, and how safely you can evolve request and response contracts.

We will not use this early in the guide because versioning a hello-world endpoint would be theatre. Chapter 10 introduces it when the API has enough shape for the tradeoffs to matter.

## HTTP Service Clients Auto-Configuration

!!! note "Spring Boot 4 only"
    HTTP service client auto-configuration is new in Spring Boot 4. We will use it in chapter 11.

Spring Framework already supports declarative HTTP service interfaces with `@HttpExchange`. Spring Boot 4 makes those clients easier to auto-bind in an application, so an interface can become a real HTTP client without hand-wiring all the plumbing.

For the blocking path, the client is built on the JDK `HttpClient`. That keeps the default stack close to the Java platform rather than forcing every application through a separate HTTP client dependency.

This also connects to virtual threads. When `spring.threads.virtual.enabled=true`, the blocking client path can use virtual threads in a way that keeps the code direct while improving concurrency characteristics for I/O-heavy work.

## Virtual Threads: The Truth

Virtual threads are not on by default in Spring Boot 4. You opt in by setting `spring.threads.virtual.enabled=true`.

When virtual threads are enabled, Spring Boot can use them for request handling in servlet applications. The JDK `HttpClient` path used by Boot's HTTP service client support also defaults to virtual-thread execution when that option is enabled.

This is powerful, but it is not magic. Virtual threads help most when work is mostly blocking I/O. They do not make slow database queries fast, remove the need for connection pool sizing, or turn CPU-heavy code into free parallelism.

!!! info "Truth"
    If virtual threads are affecting your application, someone opted in. Do not assume a Spring Boot 4 app is using them just because it runs on a modern JDK.

## Programmatic Bean Registration

Spring Framework 7 adds a new programmatic bean registration API on `GenericApplicationContext`. It gives library and infrastructure code a more direct way to register beans without declaring `@Bean` methods or relying on annotation scanning.

Most application code should still use the normal Spring model: components, configuration classes, and explicit `@Bean` methods where they make sense. Programmatic registration is useful, but it is a niche tool.

We will show it in an appendix because it is important for understanding the direction of the framework, especially for infrastructure authors, but it is not something we need in the main follow-along path.

## What Did Not Change

Spring's dependency injection semantics did not become a new thing. Constructors are still the preferred way to express required dependencies. Bean scopes, conditional configuration, profiles, and property binding still behave like Spring concepts, not like a brand-new framework.

The controller model is also familiar. `@RestController` and `@GetMapping` still mean what older Spring MVC developers expect them to mean. `application.yml` remains a normal place for externalized configuration, and actuator still exposes operational endpoints through the same broad surface area.

Spring Data JPA APIs are still recognizable, and Spring Security still centers servlet applications around the `SecurityFilterChain` pattern. There are version-specific details in Spring Security 7, but the architectural shape is continuous with modern Spring Security 6.

!!! info "Truth"
    Most existing Spring Boot 3 knowledge transfers. The danger is not that everything changed; the danger is assuming nothing changed and copying stale dependency names or test APIs.

## Migrating From Spring Boot 3

If you already have a Spring Boot 3 application, you do not need to relearn Spring from zero. You need a migration pass: dependency names, Jakarta EE 11 library compatibility, Jackson 3 behavior, test annotations, and any framework APIs that moved with the modularization work.

The main guide is written as a clean Spring Boot 4 path, not as a migration diary. That keeps the chapters focused on the target state instead of constantly comparing every line to the old one.

For a direct upgrade checklist, use the Spring Boot 3 to Spring Boot 4 migration appendix. That appendix is where we collect compatibility checks, dependency replacement notes, and practical upgrade sequencing.

## What We Will and Will Not Use

We will use WebMVC as the main web stack in Part II. We will use JPA with Postgres in Part III, Spring Security 7 in Part IV, and Actuator, Micrometer, and OpenTelemetry in Part V. We will also use Docker, GitHub Actions, and Fly.io so the application moves through a realistic local-to-production path.

We will not use WebFlux as the main path. Reactive Spring is important, but mixing it into the primary servlet story would make the guide harder to follow. WebFlux belongs in an appendix where it can be treated on its own terms.

We will not use Kotlin, native image, or XML configuration in the main path. Kotlin and native image are both useful enough to deserve appendix treatment. XML configuration is mostly historical for the kind of application we are building here.

Chapter 5 returns to the application and upgrades the test story from controller-level confidence to full HTTP integration testing.
