# OpenAPI Documentation

So far, this guide has treated the API as something you call directly. You have used `curl`, HTTPie-style requests, `MockMvcTester`, and full application tests to prove behavior. That does not change in this chapter. OpenAPI is documentation for the API surface. It is not our test harness, and Swagger UI is not the source of truth for whether an endpoint works.

What we add here is a discoverable, machine-readable, human-browsable description of the same endpoints you already know how to call.

OpenAPI 3 is the current name for the specification that grew out of the Swagger ecosystem. People still say "Swagger" in two different ways: sometimes they mean the old Swagger specification, and sometimes they mean Swagger UI, the browser-based renderer for an OpenAPI document. In this chapter, the distinction matters:

- `/v3/api-docs` is the generated OpenAPI JSON document.
- `/swagger` is the Swagger UI page that renders that document in a browser.

The JSON document is the portable artifact. Tools can read it to generate clients, validate documentation drift, feed API catalogs, or publish docs in a separate portal. Swagger UI is a convenient local view over the same document.

The sample project is a normal Spring Boot 4 WebMVC application:

```xml
{% include-markdown "../../code/16-openapi/maven/pom.xml" comments=false %}
```

For Spring Boot 4, this chapter uses the Spring Boot 4-compatible `springdoc-openapi-starter-webmvc-ui` line. The dependency is versioned explicitly in the Maven property so the docs and the build agree about which springdoc release is being demonstrated.

## What Springdoc Does

`springdoc-openapi` watches the Spring MVC application at runtime. It looks at controller mappings, HTTP methods, request bodies, response types, path variables, query parameters, validation annotations, and Swagger/OpenAPI annotations. From that information it emits an OpenAPI document.

There is no controller code generation in this chapter. We write a controller the normal Spring MVC way, add a small amount of documentation metadata, and let springdoc describe what Spring already knows about the application.

That tradeoff is useful because the docs stay close to the real routing table. If you rename `GET /widgets/{id}` to `GET /widgets/by-id/{id}`, the generated spec follows the controller mapping. If you change the response record, the schema changes with it. You can still enrich the output with annotations, but you are not maintaining a large hand-written OpenAPI file beside the code.

The annotations you will see most often are:

- `@Operation` for a summary and longer description of one endpoint.
- `@Parameter` for path, query, header, and cookie parameter details.
- `@Schema` for model and property descriptions, examples, and constraints.
- `@ApiResponse` for documented response codes and meanings.

Use them where they add information the framework cannot infer. Do not annotate every obvious thing. A method named `list()` mapped to `GET /widgets` already tells springdoc a lot. A short `@Operation(summary = "List widgets")` is enough for this chapter.

## The Controller

The API is intentionally small:

```java
{% include-markdown "../../code/16-openapi/maven/src/main/java/dev/springboot4docs/ch_16_openapi/WidgetController.java" comments=false %}
```

There are three operations:

- `GET /widgets` returns all widgets.
- `GET /widgets/{id}` returns one widget, or `404 Not Found` when the id is missing.
- `POST /widgets` creates a widget and returns `201 Created`.

The `Widget` record is the response and request body type:

```java
record Widget(
		@Schema(description = "server-assigned identifier", example = "1")
		Long id,
		@Schema(description = "display name", example = "Dashboard")
		String name) {
}
```

Spring MVC sees this record as a JSON shape with `id` and `name` properties. Springdoc sees the same type and turns it into an OpenAPI schema component. The `@Schema` annotations add human-facing descriptions and examples to the generated schema. They do not change JSON serialization. Jackson still writes the record fields in the ordinary way.

The controller uses a few operation-level annotations:

```java
@Operation(summary = "Get one widget")
@ApiResponse(responseCode = "200", description = "Widget found")
@ApiResponse(responseCode = "404", description = "Widget not found")
@GetMapping("/widgets/{id}")
ResponseEntity<Widget> get(
		@Parameter(description = "Widget identifier", example = "1")
		@PathVariable Long id) {
```

Spring can infer that `id` is a path variable and that the endpoint handles `GET /widgets/{id}`. It cannot infer the phrase "Widget identifier", and it may not know every response code you consider part of the public contract. That is the kind of information worth adding.

For bigger APIs, this is also where discipline matters. Documentation annotations should clarify the contract, not duplicate the implementation. If an annotation repeats the method name in different words, skip it. If it explains a status code, a domain term, an accepted format, or a non-obvious body shape, keep it.

## API Metadata

OpenAPI documents have top-level metadata. That is where you set the API title, version, contact, license, external docs, and server list. Springdoc lets you do that with an `OpenAPI` bean:

```java
{% include-markdown "../../code/16-openapi/maven/src/main/java/dev/springboot4docs/ch_16_openapi/OpenApiConfig.java" comments=false %}
```

The title and version appear at the top of Swagger UI and in the JSON document under `info`. The contact block gives readers a place to go when the API contract is unclear. A production API might also add a license:

```java
new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0")
```

You can also add servers when the generated base URL is not the one consumers should use. That comes up behind gateways, reverse proxies, and public API domains:

```java
new Server().url("https://api.example.com").description("Production")
```

The sample keeps metadata short because the main point is the mechanics: one Spring bean customizes the top-level OpenAPI object.

## Swagger UI Path

The application configuration is equally small:

```yaml
{% include-markdown "../../code/16-openapi/maven/src/main/resources/application.yml" comments=false %}
```

By default, springdoc serves Swagger UI at a longer generated UI path. This chapter shortens the browser entry point to `/swagger`:

```yaml
springdoc:
  swagger-ui:
    path: /swagger
```

This does not move the OpenAPI JSON document. The spec is still available at `/v3/api-docs`. The property only changes the human-browsable Swagger UI route.

You can also customize the JSON route with `springdoc.api-docs.path`, but this guide leaves it alone. `/v3/api-docs` is a widely recognized convention and is easy for tools and humans to find.

## Testing The Spec Endpoint

The test is a Spring MVC slice test with `MockMvcTester`:

```java
{% include-markdown "../../code/16-openapi/maven/src/test/java/dev/springboot4docs/ch_16_openapi/OpenApiWebMvcTest.java" comments=false %}
```

The test does not click through Swagger UI. It treats the OpenAPI document as an HTTP endpoint and asserts the important behavior:

```java
this.mvc.get().uri("/v3/api-docs")
		.assertThat()
		.hasStatusOk()
		.hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
		.bodyJson()
		.extractingPath("$.paths")
		.asMap()
		.containsKeys("/widgets", "/widgets/{id}");
```

That is enough for this chapter. We are not validating every generated schema field. We are proving that the documentation endpoint is present and that it includes the widget routes that make up this sample API.

The generated application test remains the basic context-load check:

```java
{% include-markdown "../../code/16-openapi/maven/src/test/java/dev/springboot4docs/ch_16_openapi/ApplicationTests.java" comments=false %}
```

Run the tests from the chapter project:

```bash
./mvnw -q -B test
```

## Run It

Start the application:

```bash
./mvnw spring-boot:run
```

Open Swagger UI in a browser:

```text
http://localhost:8080/swagger
```

You should see the `Widget API` title from `OpenApiConfig`, the `1.0.0` version, and the three widget operations.

Now fetch the machine-readable document directly:

```bash
curl -s http://localhost:8080/v3/api-docs
```

The response is a JSON OpenAPI document. The exact ordering is not important, but it contains a top-level `openapi` version, an `info` object, and a `paths` object. Inside `paths`, you should see entries like:

```json
{
  "/widgets": {
    "get": {},
    "post": {}
  },
  "/widgets/{id}": {
    "get": {}
  }
}
```

Swagger UI reads that same document. It is not a separate source of API behavior.

You can still call the endpoint directly:

```bash
curl -i http://localhost:8080/widgets
```

Create a widget the same way you created resources in earlier chapters:

```bash
curl -i -X POST http://localhost:8080/widgets \
  -H 'Content-Type: application/json' \
  -d '{"name":"Reports"}'
```

Swagger UI has a "try it out" feature, but this guide does not rely on it. Browser buttons are convenient for exploration. They are not repeatable documentation examples, they do not belong in shell history, and they are not tests. We continue to drive examples with `curl`, HTTPie-style commands, `MockMvcTester`, and `RestTestClient`.

## Grouping APIs

Large applications often need more than one OpenAPI view. Internal endpoints, public endpoints, admin endpoints, and partner endpoints may have different audiences. Springdoc supports this with `GroupedOpenApi` beans. A group can include or exclude paths and packages so `/v3/api-docs/public` describes one subset while `/v3/api-docs/admin` describes another.

You do not need grouping for three widget endpoints. Keep a single document until the API has a real audience split.

## Security Schemes

OpenAPI can describe authentication too. For bearer tokens, API keys, and OAuth2 flows, you declare security schemes and attach them globally or per operation. Swagger UI can then show an authorize button and send the right header when someone explores the API interactively.

This chapter avoids security configuration because Part IV handles security properly. The important point is that the OpenAPI document can describe the security contract, but it does not enforce it. Spring Security enforces access. OpenAPI documents what clients need to send.

## Where This Leaves Part II

Part II now has the main pieces of a Spring Boot MVC API: controllers, JSON, validation, error handling, versioning, HTTP clients, filters, CORS, file I/O, async responses, virtual threads, and OpenAPI documentation.

OpenAPI gives the API a discoverable surface. It helps humans browse, tools inspect, and clients understand the contract. It does not replace the habits we have built through the web chapters. The examples are still command-line calls and tests because those are repeatable.

Part III moves down a layer into persistence. The same rule will carry forward: build the feature first, then document and test the contract at the boundary where other code depends on it.
