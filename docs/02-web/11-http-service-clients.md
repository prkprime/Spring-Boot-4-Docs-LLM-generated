# HTTP Service Clients

!!! note "Spring Boot 4 only"
    Spring Boot 4 is the first Spring Boot generation in this guide where declarative HTTP service clients are part of the main application story. Spring Framework supplies `@HttpExchange` interfaces and the proxy machinery; Boot makes that style fit naturally in a configured application.

Most Spring developers arrive at outbound HTTP with one of three mental models. `RestTemplate` is the older blocking client and still appears in a lot of production code. `WebClient` is the reactive client from WebFlux, useful when the rest of the call path is reactive. `RestClient`, introduced in Spring Framework 6, is the modern blocking client with a fluent API and no reactive programming model required.

HTTP service interfaces sit one level above those clients. Instead of writing `restClient.get().uri(...).retrieve().body(...)` at every call site, you declare a Java interface and annotate it with the HTTP contract. Spring creates a proxy. Your application injects that proxy as a normal bean. The calling code sees a method call; the proxy turns that method call into an HTTP request.

That is the new path to keep in your head: declare an interface, import it, get a bean.

## Before Boot 4

Spring Framework already had HTTP service interfaces before Spring Boot 4. The difference was how much wiring you had to write yourself. A typical blocking setup looked like this:

```java
@Bean
GitHubClient githubClient(RestClient.Builder builder) {
	RestClient restClient = builder
			.baseUrl("https://api.github.com")
			.build();

	HttpServiceProxyFactory factory = HttpServiceProxyFactory
			.builderFor(RestClientAdapter.create(restClient))
			.build();

	return factory.createClient(GitHubClient.class);
}
```

That still works. It is also useful when you want very explicit one-off construction. The drawback is repetition. Every client needs the same proxy factory pattern, and the interesting part of the application gets buried under adapter code.

With the Spring Boot 4 style, the interface is registered with `@ImportHttpServices`, and the application provides normal configuration for the client. The framework registry creates the proxy bean, so controllers and services can inject the interface directly.

!!! note "What Boot adds"
    The important shift is not that `@HttpExchange` exists. That comes from Spring Framework. The Boot 4-era application style is that HTTP service interfaces can be registered as application clients instead of being manually created with `HttpServiceProxyFactory` in every project.

## The Interface

The response model is just a record matching the fields this chapter cares about from GitHub's repository response:

```java
{% include-markdown "../../code/11-http-service-clients/maven/src/main/java/dev/springboot4docs/ch_11_http_service_clients/GitHubRepo.java" comments=false %}
```

The client is a Java interface:

```java
{% include-markdown "../../code/11-http-service-clients/maven/src/main/java/dev/springboot4docs/ch_11_http_service_clients/GitHubClient.java" comments=false %}
```

`@HttpExchange` marks the type as an HTTP service. `@GetExchange` marks one method as a `GET` request. The path can contain URI template variables such as `{owner}` and `{repo}`. Method parameters annotated with `@PathVariable` fill those variables, exactly like the inverse side of a Spring MVC controller.

The same idea applies to the other request shapes you already know. Use `@PostExchange`, `@PutExchange`, `@PatchExchange`, and `@DeleteExchange` for other HTTP methods. Use `@RequestParam` for query parameters, `@RequestBody` for a JSON body, and `@RequestHeader` for headers. If you have written `@RestController` methods, the annotation vocabulary is intentionally familiar; you are now describing the client side of the same HTTP contract.

Keep the interface focused on the remote API, not on your local application's use case. `GitHubClient.getRepo(owner, repo)` is a remote operation. A method such as `getInterestingSpringBootStats()` would mix application meaning into the transport boundary. Put that kind of orchestration in a service that calls the client. The interface should be easy to compare with the remote API documentation: method, path, path variables, query parameters, body, headers, and return type.

Return types should also describe successful responses. A record is a good fit when the response body is JSON and the application only needs a stable subset of fields. You do not have to model the entire remote response. Extra JSON fields are ignored by normal Jackson binding, so a small DTO can be a deliberate anti-corruption layer between the remote API and your own code. If the remote API changes or adds fields, the rest of your application does not automatically inherit that shape.

This sample uses a type-level `@HttpExchange` URL placeholder for the base URL:

```java
@HttpExchange(url = "${spring.http.client.service.dev.springboot4docs.ch_11_http_service_clients.GitHubClient.base-url}")
```

That keeps the base URL out of controller code and makes the test able to replace it. In an application with several service interfaces, use one property key per interface so each downstream service can move independently.

## Register The Client

The configuration class is small:

```java
{% include-markdown "../../code/11-http-service-clients/maven/src/main/java/dev/springboot4docs/ch_11_http_service_clients/HttpClientsConfig.java" comments=false %}
```

`@ImportHttpServices(types = GitHubClient.class)` tells Spring to register that interface as an HTTP service client. At startup, Spring creates a proxy-backed bean for `GitHubClient`. Nothing in the controller has to know about `RestClient`, `HttpServiceProxyFactory`, or an HTTP adapter.

You can also use package scanning with `basePackages` or `basePackageClasses` when an application has many clients. For a teaching sample, explicit `types` is easier to read and avoids accidentally importing unrelated interfaces.

HTTP service groups are useful once an application has more than one downstream API. A group can share client-level settings such as interceptors, observation configuration, authentication, and default headers. Start explicit, then introduce grouping when there is real shared behavior. Do not group clients just because they are all HTTP clients; a payment API, a search API, and an internal catalog API often need different timeouts, credentials, and retry rules.

The default blocking client path is based on Spring's `RestClient` HTTP service adapter. In a Spring Boot 4 MVC application, the underlying transport is the JDK `HttpClient` by default. That matters because it is a standard JDK client, requires no Apache HTTP Components dependency for the basic path, and works well with virtual threads when the application opts in.

## Configuration

The application configuration has two parts: the GitHub base URL and virtual-thread opt-in.

```yaml
{% include-markdown "../../code/11-http-service-clients/maven/src/main/resources/application.yml" comments=false %}
```

The long key under `spring.http.client.service` is deliberate. It is the fully qualified interface name, followed by client-specific settings. This chapter uses:

```yaml
spring:
  http:
    client:
      service:
        dev.springboot4docs.ch_11_http_service_clients.GitHubClient:
          base-url: https://api.github.com
```

A production application usually has different values per environment. Local development might point at a sandbox, tests point at a stub, staging points at a pre-production dependency, and production points at the real service. Keeping the URL in configuration is what makes that possible.

The key name is long, but it has an advantage: it is unambiguous. If two teams both create an interface named `InventoryClient` in different packages, their fully qualified names are still distinct. The tradeoff is that renaming or moving the interface changes the property key. Treat public client interfaces like other configuration-bound types: once they are used in deployed configuration, rename them deliberately and update the environment alongside the code.

The second setting is:

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

Virtual threads are not required for HTTP service clients. They are useful for blocking I/O applications because each in-flight request can block without tying up a scarce platform thread. The programming model stays synchronous: controller calls client method, client waits for the response, controller returns the result. The difference is how cheaply the JVM can park that waiting work.

!!! note "Virtual threads are opt-in"
    Spring Boot 4 does not turn virtual threads on just because the app runs on a modern JDK. Set `spring.threads.virtual.enabled=true` when you want that execution model.

## The Controller

The controller injects `GitHubClient` like any other Spring bean:

```java
{% include-markdown "../../code/11-http-service-clients/maven/src/main/java/dev/springboot4docs/ch_11_http_service_clients/RepoController.java" comments=false %}
```

There is no HTTP-client construction here. `GET /repos/{owner}/{repo}` calls the generated client proxy and returns the unmarshaled `GitHubRepo` record. Spring MVC then serializes that record to JSON for the caller.

This is the main design benefit of the interface style. The outbound HTTP contract is centralized in `GitHubClient`. The inbound HTTP contract is centralized in `RepoController`. The controller does not build URLs by hand, concatenate path segments, or parse JSON. It orchestrates application behavior.

## Tests

The first test is a focused MVC test. It proves the controller wiring without using the generated HTTP proxy or the network:

```java
{% include-markdown "../../code/11-http-service-clients/maven/src/test/java/dev/springboot4docs/ch_11_http_service_clients/RepoControllerWebMvcTest.java" comments=false %}
```

`@WebMvcTest(RepoController.class)` loads the MVC slice around the controller. `@MockitoBean GitHubClient` replaces the real HTTP service bean with a Mockito mock. The test stubs the client, calls `/repos/spring-projects/spring-boot`, asserts the JSON response, and verifies the controller passed the expected path values to the client.

That kind of test should stay boring. It is not trying to prove Spring's HTTP proxy implementation. It is proving your controller contract and delegation.

The second test loads the Spring Boot context and exercises the generated proxy:

```java
{% include-markdown "../../code/11-http-service-clients/maven/src/test/java/dev/springboot4docs/ch_11_http_service_clients/GitHubClientSpringBootTest.java" comments=false %}
```

This test uses `MockRestServiceServer` instead of the real internet. The test configuration attaches the mock server to the `RestClient.Builder` used by the HTTP service group. The test then expects a request to `http://localhost/repos/spring-projects/spring-boot`, returns a JSON body, calls `github.getRepo(...)`, and verifies both the Java record and the HTTP expectation.

The important testing rule is simple: do not make follow-along tests depend on GitHub, DNS, credentials, rate limits, or a working network. A local mock server or `MockRestServiceServer` gives you the same client-side behavior with deterministic input.

Use both test shapes for different risks. The MVC test catches mistakes in your inbound route, JSON response, and controller delegation. The Spring Boot test catches mistakes in the declarative client: the imported interface, the configured base URL, the path template, JSON decoding, and the fact that the generated bean can be injected. Neither test needs the public internet, and neither test has to know GitHub's current star count.

Run the chapter tests from the Maven project:

```bash
./mvnw -q -B test
```

## Production Notes

Timeouts should be explicit. A client with no practical timeout can consume request capacity long after the caller has given up. For Boot-managed HTTP clients, start with `spring.http.client.connect-timeout` and `spring.http.client.read-timeout`, then tune per downstream service when one dependency has a different latency profile. Shorter is not always better; choose values that match the remote service's real behavior and your own API's budget.

Retries are not automatic just because a method is declarative. Retrying `GET` for a transient network failure can be reasonable. Retrying a non-idempotent `POST` can duplicate work unless the API has idempotency keys or another safety mechanism. Keep retry policy outside the interface contract: use a resilience library such as Resilience4j, a service-layer wrapper, or a client customizer where the behavior is visible and testable.

Error handling follows the underlying client behavior. Successful responses are unmarshaled into the declared return type. Client and server error responses become exceptions by default, such as `HttpClientErrorException` for 4xx and `HttpServerErrorException` for 5xx in the blocking `RestClient` path. That is usually what you want at the HTTP boundary: the normal method return type represents a successful response, and failures move into exception handling where you can translate them into your application's error model.

Also decide where authentication belongs. If every GitHub request needs the same token, a client customizer or interceptor is cleaner than adding `@RequestHeader` to every method. If a header changes per caller, keep it as a method parameter so the contract is explicit.

Observability belongs here too. Outbound HTTP calls are often where latency and failures enter an otherwise healthy application. Give each client a clear name, keep base URLs in configuration, and make sure metrics or logs can distinguish one downstream dependency from another. A declarative interface should make the call easier to read; it should not make the remote dependency invisible during operations.

## Run It

Start the application:

```bash
./mvnw spring-boot:run
```

Call the local controller:

```bash
curl -i http://localhost:8080/repos/spring-projects/spring-boot
```

The application calls the configured GitHub base URL through the generated `GitHubClient` bean and returns the repository fields selected by `GitHubRepo`.

Chapter 12 moves from calling another HTTP API to documenting and exercising your own API as the web layer grows.
