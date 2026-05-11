# API Versioning

!!! note "Spring Boot 4 only"
    The API versioning auto-configuration in this chapter is new in Spring Boot 4, built on Spring Framework 7's first-class API versioning support. Earlier Spring MVC applications usually had to customize `RequestMappingHandlerMapping` or write their own mapping condition infrastructure.

APIs change because products change. New fields appear, old fields become misleading, validation rules tighten, and response shapes that were enough in version 1 stop being enough in version 2. The hard rule is that a published API cannot break callers just because the server team wants a cleaner model. A mobile app, batch job, partner integration, or old internal service may keep calling the old contract for months or years. Versioning gives the server room to evolve while keeping those callers on a contract they already understand.

Versioning does not make breaking changes cheap. It makes them explicit. The moment you introduce a new API version, you have two contracts to test, monitor, document, and eventually retire. Deprecation is a long process: announce the old version, give clients time to move, watch traffic, and only remove the version when the remaining risk is acceptable. Spring Boot 4 does not solve that product problem, but it removes a lot of framework plumbing from the HTTP side.

The cleanest versioning policy is still to avoid new versions for additive changes. Adding an optional response field, accepting a new optional request field, or supporting a new enum value that old clients can ignore usually does not need a new API version. A new version is for contract changes that old clients cannot safely consume: removing a field, changing a field's meaning, changing validation in a way that rejects formerly valid requests, changing pagination semantics, or returning a different representation for the same resource. Treat a version as a compatibility boundary, not as a release number.

## Strategies

The header strategy puts the version in a request header such as `X-API-Version: 2`. This chapter uses it as the primary example because it keeps URLs stable and makes the default-version story straightforward. A client can call `/products/1` for both versions and choose the contract with a header. The tradeoff is visibility: a version in a header is less obvious when someone copies a URL from a log or browser.

The path-segment strategy puts the version directly in the URL, such as `/v2/products/1`. It is the most visible option and often the easiest for humans to understand. It is also the hardest to change once it spreads through documentation, bookmarks, gateways, dashboards, and client code. Use it when URL visibility matters more than URL stability.

The media-type parameter strategy puts the version in content negotiation, commonly with an `Accept` header such as `Accept: application/vnd.example+json;v=2`. This is the REST purist's pick because the client asks for a representation format, not a different resource path. Many client teams dislike it because it is harder to type, harder to test manually, and easier to lose inside generic HTTP tooling.

The query-parameter strategy uses a URL like `/products/1?version=2`. It is ugly, but it is simple. It works well with browsers, links, caches that key by query string, and quick manual tests. The downside is that versioning becomes another query option beside filters and pagination, even though it controls the whole API contract.

Each strategy is exclusive at runtime. Pick one resolver for an application or gateway boundary. Mixing version sources sounds flexible, but it usually creates ambiguity: what should happen if the path says `v1` and the header says `2`?

## What's New

Spring Framework 7 added version-aware request mappings. The familiar mapping annotations now have a `version` attribute, so two handlers can use the same HTTP method and path while declaring different API versions:

```java
@GetMapping(value = "/{id}", version = "1")
```

Spring Boot 4 adds auto-configuration for the version strategy. Instead of extending MVC configuration support or replacing the request mapping handler, you set `spring.mvc.apiversion.*` properties. Boot creates the MVC API version strategy, connects it to Spring MVC's handler mapping, and Spring uses the version condition when selecting a controller method.

That distinction matters for follow-along projects. We are not teaching a clever custom annotation or a local `HandlerMapping` subclass. The interesting bit is the built-in contract between Spring Boot configuration and Spring MVC mapping selection. The application code declares what version each handler serves; configuration declares where the version comes from; the framework combines those two facts during request mapping.

The configuration knobs are:

```properties
spring.mvc.apiversion.use.header=X-API-Version
spring.mvc.apiversion.use.path-segment=0
spring.mvc.apiversion.use.media-type-parameter[application/vnd.example+json]=v
spring.mvc.apiversion.use.query-parameter=version
spring.mvc.apiversion.supported=1,2
spring.mvc.apiversion.required=true
spring.mvc.apiversion.default=1
```

In the local Spring Boot 4.0.6 metadata, the default-version property is exposed as `spring.mvc.apiversion.default`. Also note that `required=true` and a default version are mutually exclusive: if a default is configured, Spring can handle a missing client version by using that default. If `required=true` is configured, a missing version is an error.

## Configuration

The sample application uses the header strategy. It supports versions `1` and `2`, and it defaults requests with no header to version `1`:

```yaml
{% include-markdown "../../code/10-api-versioning/maven/src/main/resources/application.yml" comments=false %}
```

The commented alternatives are there deliberately. To use path-segment, media-type-parameter, or query-parameter versioning, replace the `header` line with exactly one of those alternatives and adjust clients and tests around that strategy.

For path segments, `path-segment: 0` means Spring reads the first path segment as the version. A request path like `/v2/products/1` has `v2` in segment zero. For query parameters, `query-parameter: version` reads `/products/1?version=2`. For media types, the YAML map key is the media type and the value is the parameter name, so `application/vnd.example+json: v` reads `v=2` from the `Accept` header.

The `supported` list rejects versions outside the advertised set. In this sample, `X-API-Version: 99` does not quietly fall through to some random handler. It becomes a client error. That is a useful guardrail because unsupported versions are usually client bugs or stale documentation.

Defaults deserve a deliberate policy. A default version is useful when an existing unversioned API becomes versioned and you need older callers to keep working. It is less useful for a brand-new public API where every client can be required to send a version from day one. For those APIs, omit the default and set `required: true` instead. Spring will then reject requests that do not declare a version, which makes client mistakes visible during integration instead of months later.

## Product API

The response models are intentionally separate. Version 1 exposes only `id` and `name`:

```java
{% include-markdown "../../code/10-api-versioning/maven/src/main/java/dev/springboot4docs/ch_10_api_versioning/ProductV1.java" comments=false %}
```

Version 2 keeps those fields and adds `priceCents`:

```java
{% include-markdown "../../code/10-api-versioning/maven/src/main/java/dev/springboot4docs/ch_10_api_versioning/ProductV2.java" comments=false %}
```

The service is not the point of this chapter. It is a tiny collaborator so the MVC slice can use `@MockitoBean` and keep the tests focused on HTTP routing:

```java
{% include-markdown "../../code/10-api-versioning/maven/src/main/java/dev/springboot4docs/ch_10_api_versioning/ProductService.java" comments=false %}
```

The controller is where the versioning feature becomes visible:

```java
{% include-markdown "../../code/10-api-versioning/maven/src/main/java/dev/springboot4docs/ch_10_api_versioning/ProductController.java" comments=false %}
```

Both methods are `GET /products/{id}`. Without API versioning, that would be an ambiguous mapping. With Spring Framework 7's `version` condition, they are two distinct mappings. `X-API-Version: 1` selects `getProductV1`; `X-API-Version: 2` selects `getProductV2`; no header selects version `1` because the application configured a default.

The version 1 method also sets deprecation headers. That is not required by Spring's versioning support, but it is part of operating a real versioned API. Returning the old shape and warning clients at the same time is how you create a migration path without breaking callers.

Keeping the two response records separate is also intentional. It is tempting to use one `Product` type with nullable fields and conditional serialization. That works for very small differences, but it blurs the contract once versions diverge. Separate DTOs make the old and new shapes obvious in code review, OpenAPI generation, tests, and client examples. Shared domain logic can still live behind the controller; the HTTP layer should be honest about the shape it sends.

## Tests

The tests use the Spring Boot 4 MVC slice, `MockMvcTester`, and `@MockitoBean`:

```java
{% include-markdown "../../code/10-api-versioning/maven/src/test/java/dev/springboot4docs/ch_10_api_versioning/ProductControllerWebMvcTest.java" comments=false %}
```

The first test sends `X-API-Version: 1` and asserts the v1 shape. It checks the product name and then checks the raw response body does not contain `priceCents`. That absence matters. A version is not just a code path; it is a response contract.

The second test sends `X-API-Version: 2` and asserts `priceCents` is present with the expected value. Notice that the URI is still `/products/1`. The version header, not the path, chooses the handler.

The third test sends no version header. Because the application configured `spring.mvc.apiversion.default: 1`, Spring routes the request to the version 1 handler. This is a practical rollout choice when you introduce versioning after an API already exists: old clients that never heard of version headers can keep receiving the old contract.

The last test sends `X-API-Version: 99`. It asserts a non-success status rather than overfitting the exact error body. The important contract here is that unsupported versions are rejected; clients should not get a successful response for a version the server does not support.

Run the chapter tests from the Maven project:

```bash
./mvnw -q -B test
```

This project also configures Surefire to run Mockito as a Java agent, matching the earlier Mockito-based chapters. That keeps `@MockitoBean` working on newer JDKs where Mockito cannot rely on self-attachment.

## Deprecating Version 1

Versioning and deprecation are separate concerns. Versioning lets the server route `1` and `2` differently. Deprecation tells clients that version `1` still works today but is on the way out.

The sample shows the simplest controller-level approach:

```java
response.setHeader("Deprecation", "@1767225600");
response.setHeader("Sunset", "Fri, 31 Dec 2027 23:59:59 GMT");
```

`Deprecation` is standardized by RFC 9745 and communicates that the selected resource or behavior is deprecated. The value above is a structured-field date for January 1, 2026. `Sunset` gives clients a later date when the server expects the old behavior to stop being available. In a production API, you would usually centralize this in an interceptor or response advice so every v1 endpoint gets the same headers.

Headers are only part of the process. You still need release notes, dashboards, client outreach, and a removal plan. The HTTP headers matter because they travel with every response. They let automated clients, contract tests, API gateways, and observability tools notice that a deprecated version is still in use.

A common production pattern is to log the selected API version for every request. Once that value is in access logs or metrics, you can answer the real removal question: who is still calling v1? Without that telemetry, teams end up guessing from code search, support tickets, or gateway configuration. Versioning should come with visibility from the beginning, because the retirement work starts the day the new version ships.

## Run It

Start the application:

```bash
./mvnw spring-boot:run
```

Ask for version 1:

```bash
curl -i http://localhost:8080/products/1 \
  -H 'X-API-Version: 1'
```

The response body is the old shape:

```json
{
  "id": 1,
  "name": "Widget"
}
```

Ask for version 2:

```bash
curl -i http://localhost:8080/products/1 \
  -H 'X-API-Version: 2'
```

Now the response includes the new field:

```json
{
  "id": 1,
  "name": "Widget",
  "priceCents": 1999
}
```

Try the request with no header:

```bash
curl -i http://localhost:8080/products/1
```

It falls back to version 1. Try an unsupported version:

```bash
curl -i http://localhost:8080/products/1 \
  -H 'X-API-Version: 99'
```

That request fails instead of silently choosing another contract.

Spring Boot 4's contribution is the auto-configuration: choose a resolver with properties, declare versions on mappings, and let MVC choose the right handler. Chapter 11 moves from versioned request routing to the next web concern: documenting and exercising the API as it grows.
