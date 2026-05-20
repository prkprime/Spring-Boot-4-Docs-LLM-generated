# Error Handling + ProblemDetail

Uniform error responses are part of the API contract. A client should not have to parse one JSON shape for validation, another for missing resources, and a plain text body for business-rule failures. When errors are consistent, client code can branch on status, `type`, and extension fields instead of scraping messages. Operations teams also benefit: dashboards can group failures by problem type, alert on a spike in one class of error, and connect a customer-visible failure to an internal trace.

Spring Boot 4 gives us a good default foundation for this: Spring Framework's `ProblemDetail` support and `ResponseEntityExceptionHandler`. This chapter replaces the small validation-only handler from chapter 8 with a handler that keeps Spring MVC's built-in behavior and adds domain-specific details where the application knows more than the framework.

## RFC 9457

RFC 9457, "Problem Details for HTTP APIs", is the current standard for machine-readable HTTP errors. It obsoletes the older RFC 7807, but the model is intentionally familiar. A problem response is a JSON object with these standard members:

- `type`: a URI identifying the category of problem. The default is `about:blank`, but real APIs should use stable type URIs for domain errors.
- `title`: a short human-readable summary, usually derived from the HTTP status.
- `status`: the HTTP status code repeated in the body.
- `detail`: a human-readable explanation for this specific occurrence.
- `instance`: a URI identifying this occurrence, commonly the request path.

The object can also contain extension properties. Extensions are where an API adds structured details such as validation field errors, a failed `sku`, an `errorId`, or a `traceId`. The rule is simple: keep standard fields standard, and put application-specific data in named extensions.

Spring represents this shape with `org.springframework.http.ProblemDetail`. When a controller or exception handler returns a `ProblemDetail`, Spring writes the response using the `application/problem+json` media type.

!!! tip "Problem JSON content type"
    Do not assert only `application/json` in tests for problem responses. The media type for RFC 9457 JSON is `application/problem+json`, and Spring MVC will use it for `ProblemDetail` bodies.

Think of a problem document as two contracts at once. The HTTP status tells generic HTTP clients how the request failed. A proxy, retry library, or browser developer tool already understands the difference between `400`, `404`, and `409`. The problem `type` tells your API clients which application-level failure happened inside that broad status family. Two failures can both be `409 Conflict` while still needing different client behavior.

That distinction keeps the design from drifting toward message parsing. A message such as `"SKU SOLDOUT is out of stock"` is useful for a human, but it is a poor machine contract. It may be translated, rewritten by a product team, or changed to include more context. A stable type URI can stay the same while the human wording improves over time.

Type URIs do not have to point to live documentation on day one, but choose values that could become documentation later. A URI such as `https://api.example/errors/order-not-found` is clearer than a short code such as `ORDER_404`, and it avoids collisions if several systems eventually share the same error vocabulary. In a public API, serving a small documentation page at that URI is a nice follow-up. In an internal API, the URI can still act as a globally unique identifier.

Extension properties need the same discipline. Add fields that clients or operators can actually use. `errors` is useful because it maps validation messages to form fields. `sku` is useful because it identifies the inventory item that caused the conflict. `errorId` and `traceId` are useful because they connect a response to logs and traces. Avoid dumping arbitrary exception internals into the response. Stack traces, class names, SQL messages, and downstream payloads often leak implementation details and can create security or compatibility problems.

## Enable Problem Details

Spring Boot 4 enables MVC problem details by default, but this chapter sets the property explicitly so the behavior is visible in the project:

```yaml
{% include-markdown "../../code/09-error-handling/maven/src/main/resources/application.yml" comments=false %}
```

!!! note "Spring Boot 4 default"
    `spring.mvc.problemdetails.enabled` is on by default in Spring Boot 4. Keeping it in `application.yml` is still useful in teaching code because it gives the error format a named switch readers can search for.

With this enabled, Spring MVC framework failures can become `ProblemDetail` responses automatically. Examples include request validation failures, unsupported HTTP methods, unreadable request bodies, and missing resources handled by MVC. You do not need to create every framework error body from scratch.

The application still needs to describe its own domain failures. Spring cannot know that a missing order should use an `order-not-found` type URI, or that an inventory conflict should expose a `sku` extension. That is the job of `@RestControllerAdvice`.

## The Order API

The sample API is deliberately small. The request record uses the same Jakarta Bean Validation style from chapter 8:

```java
{% include-markdown "../../code/09-error-handling/maven/src/main/java/dev/springboot4docs/ch_09_error_handling/OrderRequest.java" comments=false %}
```

`sku` must be present, `qty` must be at least one, and `customerEmail` must look like an email address. In Spring Boot 4 and Spring Framework 7, these imports come from `jakarta.validation.*`, not `javax.validation.*`.

The response model is just enough for the controller to return an order:

```java
{% include-markdown "../../code/09-error-handling/maven/src/main/java/dev/springboot4docs/ch_09_error_handling/Order.java" comments=false %}
```

There are two domain exceptions. One represents a lookup failure:

```java
{% include-markdown "../../code/09-error-handling/maven/src/main/java/dev/springboot4docs/ch_09_error_handling/OrderNotFoundException.java" comments=false %}
```

The other represents a business-rule conflict:

```java
{% include-markdown "../../code/09-error-handling/maven/src/main/java/dev/springboot4docs/ch_09_error_handling/OutOfStockException.java" comments=false %}
```

The service keeps a tiny in-memory map so the chapter can focus on HTTP behavior rather than persistence. Ids outside the map fail with `OrderNotFoundException`. The special SKU `SOLDOUT` fails with `OutOfStockException`:

```java
{% include-markdown "../../code/09-error-handling/maven/src/main/java/dev/springboot4docs/ch_09_error_handling/OrderService.java" comments=false %}
```

The controller stays clean. It maps HTTP to service calls and lets exceptions move to the advice:

```java
{% include-markdown "../../code/09-error-handling/maven/src/main/java/dev/springboot4docs/ch_09_error_handling/OrderController.java" comments=false %}
```

`POST /orders` uses `@Valid @RequestBody`, so invalid JSON that binds successfully but violates constraints becomes a validation exception before `place` runs. `GET /orders/{id}` calls the service directly; if the id is unknown, the service exception becomes the handler's responsibility.

## The Global Handler

`@RestControllerAdvice` applies exception handling across controllers and writes return values as response bodies. Extending `ResponseEntityExceptionHandler` is the important design choice:

```java
{% include-markdown "../../code/09-error-handling/maven/src/main/java/dev/springboot4docs/ch_09_error_handling/GlobalExceptionHandler.java" comments=false %}
```

The parent class already knows about many Spring MVC exceptions. It handles framework-level failures such as invalid method arguments, missing request parameters, unsupported methods, media type problems, and unreadable messages. With problem details enabled, those built-in paths produce `ProblemDetail` bodies. Our code does not replace that machinery. It customizes the cases where the application needs a richer contract.

The validation override is the best example. `handleMethodArgumentNotValid` first delegates to `super.handleMethodArgumentNotValid(...)`. That gives us Spring's default `ProblemDetail`: correct status, default title, and the standard body shape. The override then adds one extension property named `errors`.

The `errors` value is a field-to-message map:

```json
{
  "errors": {
    "sku": "must not be blank",
    "qty": "must be greater than or equal to 1",
    "customerEmail": "must be a well-formed email address"
  }
}
```

This is easier for clients to use than a sentence in `detail`. A form can put `errors.customerEmail` beside the email input, and a CLI can print field-specific messages. Exact message text can still vary by validation provider and locale, so tests should usually assert the field keys and the overall shape, not every word.

The custom exception handlers use `ProblemDetail.forStatusAndDetail(...)`. That factory sets the HTTP status and a per-occurrence detail message. The handler then sets a stable `type` URI. This is the high-leverage move in a problem-details design: clients should branch on `https://api.example/errors/order-not-found`, not on the English phrase "Order 99 was not found".

The out-of-stock handler returns `409 Conflict`, because the request is syntactically valid but conflicts with current inventory state. Its `sku` extension gives the client a structured value to display or use in recovery. A production system might also set `errorId` or `traceId` with `setProperty()` so logs, traces, and support tickets can meet at the same identifier.

Choosing the status code still matters. `400 Bad Request` is right for malformed or invalid input at the HTTP boundary. `404 Not Found` is right when the target resource is not available to the caller. `409 Conflict` is a good fit when the request is valid but cannot be completed because of current server-side state. Do not collapse every domain exception into `500 Internal Server Error`. A `500` says the server failed unexpectedly; these examples are expected business outcomes that deserve specific, testable responses.

The handler logs each exception path. Keep that habit. The response is for the client; the log is for operators. This chapter only uses simple one-line logging because structured logging and correlation strategy belong later in the guide.

## Tests

The tests use the Spring Boot 4 MVC slice and `MockMvcTester`:

```java
{% include-markdown "../../code/09-error-handling/maven/src/test/java/dev/springboot4docs/ch_09_error_handling/OrderControllerWebMvcTest.java" comments=false %}
```

`@WebMvcTest(OrderController.class)` loads the MVC layer around the selected controller. The test imports the service and advice so the slice has the real behavior for this chapter without starting a full application.

The validation test posts a request with three bad fields: blank `sku`, zero `qty`, and malformed `customerEmail`. It expects `400 Bad Request`, checks the content type is compatible with `MediaType.APPLICATION_PROBLEM_JSON`, and then asserts that the JSON body has `errors.sku`, `errors.qty`, and `errors.customerEmail`.

The missing-order test calls `GET /orders/99`. The service does not have that id, so the handler returns a `404` problem. The assertions check the important contract fields: `type`, `title`, `detail`, and `instance`.

The inventory test posts a valid order request for `SOLDOUT`. Validation passes, the service throws `OutOfStockException`, and the handler returns `409 Conflict` with a top-level `sku` extension. That test proves the application can add domain fields without breaking the standard problem shape.

Run the tests from the chapter project:

=== "Maven"
    ```bash
    ./mvnw test
    ```

=== "Gradle"
    ```bash
    ./gradlew test
    ```

## Run It

Start the application:

=== "Maven"
    ```bash
    ./mvnw spring-boot:run
    ```

=== "Gradle"
    ```bash
    ./gradlew bootRun
    ```

Ask for an order that exists:

```bash
curl -i http://localhost:8080/orders/1
```

Now ask for one that does not:

```bash
curl -i http://localhost:8080/orders/99
```

The response is a problem document:

```json
{
  "type": "https://api.example/errors/order-not-found",
  "title": "Not Found",
  "status": 404,
  "detail": "Order 99 was not found",
  "instance": "/orders/99"
}
```

Try a validation failure:

```bash
curl -i -X POST http://localhost:8080/orders \
  -H 'Content-Type: application/json' \
  -d '{
    "sku": "",
    "qty": 0,
    "customerEmail": "not-an-email"
  }'
```

The response is `400 Bad Request`, `Content-Type: application/problem+json`, and the body includes the `errors` extension.

Finally, try the domain conflict:

```bash
curl -i -X POST http://localhost:8080/orders \
  -H 'Content-Type: application/json' \
  -d '{
    "sku": "SOLDOUT",
    "qty": 1,
    "customerEmail": "ava@example.com"
  }'
```

That response is `409 Conflict` and includes `"sku": "SOLDOUT"`.

The main lesson is to let Spring handle framework errors, then add application meaning where the framework cannot. `ProblemDetail` gives every error the same envelope. Type URIs, extension properties, and tests turn that envelope into a reliable API contract. Chapter 10 builds on this contract when the same API starts evolving across versions.
