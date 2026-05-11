# JSON with Jackson 3

In chapter 6, the `Book` record became JSON without any controller code calling a JSON library. That was Spring MVC and Jackson working through an HTTP message converter. This chapter makes that implicit layer visible.

Spring Boot 4 uses Jackson 3. That is a new major version from the Jackson 2.x line used by Spring Boot 3. Most everyday controller code still looks the same: return a record from a `@RestController`, accept a record with `@RequestBody`, and let Spring MVC convert between Java and JSON. The differences show up when you configure Jackson directly, read older articles, or write custom serializers.

!!! note "NEW in Spring Boot 4"
    Jackson 3 moved many databind types from `com.fasterxml.jackson.databind` to `tools.jackson.databind`. Jackson annotations such as `@JsonTypeInfo` still live under `com.fasterxml.jackson.annotation`. Older Spring Boot 3 examples often import Jackson 2.x classes and will not always compile unchanged.

## Jackson 3 in One Minute

For day-to-day Spring MVC work, Jackson 3 gives you the same basic model with a cleaner baseline for modern Java. Java records are first-class DTOs. Enum values serialize as strings by default. Java time values are written as ISO-8601 text by Spring Boot's default configuration instead of numeric timestamps. Null-valued properties are included unless you configure a different inclusion rule.

Jackson 3 is also less forgiving in places where older applications sometimes leaned on loose coercion. That is a good thing for HTTP APIs. If a client sends a string where your DTO expects an object, or sends a value that cannot reasonably become the target type, you want the request to fail at the edge. A bad JSON request should not become a half-valid Java object that fails much later in application code.

The practical beginner checklist is short:

- Use records for request and response DTOs.
- Use `Instant`, `LocalDate`, and `LocalDateTime` instead of old date types.
- Keep JSON field names stable, even if Java internals change.
- Register a Jackson module when one value type needs special JSON.
- Test the JSON shape at the controller boundary.

!!! note "Date output changed for Boot users"
    Spring Boot configures Jackson with `WRITE_DATES_AS_TIMESTAMPS=false`, so `Instant` and `LocalDate` values appear as readable ISO strings. If an old tutorial expects arrays or numbers for Java time values, it is describing a different baseline.

## Spring Boot Tunables

Spring Boot exposes common Jackson settings through `spring.jackson.*` properties. You do not need to create an `ObjectMapper` bean for small policy changes.

```properties
spring.jackson.property-naming-strategy=SNAKE_CASE
spring.jackson.default-property-inclusion=non_null
spring.jackson.date-format=yyyy-MM-dd'T'HH:mm:ssXXX
```

`property-naming-strategy` can translate Java names such as `placedAt` to JSON names such as `placed_at`. Use this only when your whole API wants that convention. Changing naming strategy after clients exist is a breaking API change.

`default-property-inclusion=non_null` omits null-valued fields. That can make JSON smaller, but it also changes the meaning of absent fields for clients. Keep the default include-all behavior unless your API has a clear convention around missing values.

`date-format` affects legacy date-style values. For Java time types such as `Instant` and `LocalDate`, prefer the ISO formats that Boot already configures. They are predictable, sortable, and understood by most clients.

You can also use annotations for one-off naming or inclusion choices, but reach for properties first when the rule applies to the whole application. Global policy belongs in configuration. Local exceptions belong next to the DTO field or type that needs the exception.

## Records as DTOs

This chapter's domain uses records for every JSON-facing value:

```java
{% include-markdown "../../code/07-json-jackson/maven/src/main/java/dev/springboot4docs/ch_07_json_jackson/Money.java" comments=false %}
```

```java
{% include-markdown "../../code/07-json-jackson/maven/src/main/java/dev/springboot4docs/ch_07_json_jackson/Item.java" comments=false %}
```

```java
{% include-markdown "../../code/07-json-jackson/maven/src/main/java/dev/springboot4docs/ch_07_json_jackson/OrderStatus.java" comments=false %}
```

```java
{% include-markdown "../../code/07-json-jackson/maven/src/main/java/dev/springboot4docs/ch_07_json_jackson/Order.java" comments=false %}
```

Records are a good fit for HTTP DTOs because they make the contract explicit. The record header tells you the fields, their order in the canonical constructor, and their types. There are no setters, no Lombok annotations, and no hidden mutable state.

Jackson can serialize a record by calling its accessor methods, such as `id()` and `placedAt()`. It can deserialize a record by calling the canonical constructor with values read from JSON properties. A JSON object with `id`, `placedAt`, `deliverBy`, `total`, `status`, and `items` maps naturally to the `Order` record.

The record constructor is also a useful pressure point. If a field is required by the Java type, the record header makes that obvious. If a field is optional, model that deliberately with a nullable component, a default supplied elsewhere, or a separate request DTO. Avoid treating records as bags of fields where every property may or may not exist. That style makes the JSON contract hard to reason about and harder to validate in chapter 8.

This does not mean every internal application type must be a record. Use records at the boundary when the value is meant to be carried as data. Use ordinary classes when you need identity, lifecycle, lazy behavior, or complex invariants.

## Working with Java Time

The `Order` record has two date/time fields:

```java
Instant placedAt
LocalDate deliverBy
```

`Instant` represents a moment on the timeline. It is a good API type for something that happened at a precise time, such as when an order was placed. In JSON, Boot writes it as an ISO-8601 instant:

```json
"2026-05-09T10:15:30Z"
```

`LocalDate` represents a calendar date without a time zone. It is a good fit for delivery dates, birthdays, and other date-only concepts. In JSON, it appears as:

```json
"2026-05-12"
```

`LocalDateTime` is also supported, but use it carefully. It has a date and a time, but no offset or zone. That can be correct for "store opens at 2026-05-12T09:00 in the store's local time." It is usually not correct for audit events or cross-region ordering. For those, prefer `Instant`.

Spring Boot auto-registers Jackson's Java time support, so you do not need to add a Java time module yourself for normal MVC applications.

Keep the Java type aligned with the business meaning. Do not use `String` for dates just because JSON carries dates as strings. If the field is a timestamp, use `Instant`. If it is a date-only value, use `LocalDate`. That lets Jackson handle parsing and formatting, and it lets your Java code use date/time operations without reparsing text.

## Custom Money JSON

By default, Jackson would write `Money` as an object:

```json
{ "amount": 12.50, "currency": "USD" }
```

For this chapter we want a compact string instead:

```json
"12.50 USD"
```

That is not a global Jackson setting. It is a rule for one value type, so we write a serializer and deserializer.

```java
{% include-markdown "../../code/07-json-jackson/maven/src/main/java/dev/springboot4docs/ch_07_json_jackson/MoneySerializer.java" comments=false %}
```

The serializer receives a `Money` value and writes one JSON string. Notice the Jackson 3 imports: `JsonGenerator` comes from `tools.jackson.core`, and `ValueSerializer` and `SerializationContext` come from `tools.jackson.databind`.

The deserializer performs the reverse operation:

```java
{% include-markdown "../../code/07-json-jackson/maven/src/main/java/dev/springboot4docs/ch_07_json_jackson/MoneyDeserializer.java" comments=false %}
```

It reads the JSON string, splits it into amount and currency, and returns a new `Money` record. The error path uses `context.reportInputMismatch(...)`, which lets Jackson produce a normal JSON binding failure instead of leaking an arbitrary exception.

Now the custom pair needs to be registered. You could annotate the `Money` record directly with Jackson annotations, but a module scales better. It keeps JSON policy in configuration, and it can register several related serializers in one place.

```java
{% include-markdown "../../code/07-json-jackson/maven/src/main/java/dev/springboot4docs/ch_07_json_jackson/JacksonConfig.java" comments=false %}
```

Spring Boot finds `JacksonModule` beans and applies them to the Jackson mapper used by MVC. That means the controller can stay unaware of the custom JSON rule.

The module pattern is also easier to test and reuse. A real application might put all money, distance, country code, or domain identifier serializers in one module. The DTOs remain plain Java records, and the JSON decisions remain in one Spring configuration class.

## Polymorphism

Sometimes a JSON field can contain one of several shapes. Jackson supports that with `@JsonTypeInfo` and `@JsonSubTypes`, usually by adding a discriminator property such as `"type": "card"` or `"type": "bank_account"`.

Use this sparingly at API boundaries. Polymorphic JSON is harder for clients to produce and validate. For a closed set of Java variants, a sealed interface with record implementations is the modern Java model. If you expose that model as JSON, still make the discriminator explicit and test every subtype.

## Controller

The hard-coded sample order lives in a small component:

```java
{% include-markdown "../../code/07-json-jackson/maven/src/main/java/dev/springboot4docs/ch_07_json_jackson/OrderSamples.java" comments=false %}
```

The controller has one read endpoint and one write endpoint:

```java
{% include-markdown "../../code/07-json-jackson/maven/src/main/java/dev/springboot4docs/ch_07_json_jackson/OrderController.java" comments=false %}
```

`GET /orders/{id}` returns a hard-coded sample order through that component. The sample intentionally exercises the JSON cases from this chapter: an `Instant`, a `LocalDate`, a custom `Money` value, an enum, and a list of nested item records.

`POST /orders` accepts an `Order` request body and returns the same order. There is no repository yet. The point is to prove that the JSON shape can round-trip through Spring MVC: request JSON becomes an `Order`, then the returned `Order` becomes response JSON.

The controller does not inject `ObjectMapper`, call `writeValueAsString`, or parse the request manually. That is the boundary to keep. Controllers should talk in application types. Spring MVC and Jackson handle the HTTP representation.

This separation is what makes the controller easy to read. The method signature says what the endpoint accepts and returns. Jackson configuration says how special values are represented. Tests at the MVC boundary prove the two pieces work together.

## Controller Tests

The test uses the Spring Boot 4 MVC test slice and `MockMvcTester`:

```java
{% include-markdown "../../code/07-json-jackson/maven/src/test/java/dev/springboot4docs/ch_07_json_jackson/OrderControllerWebMvcTest.java" comments=false %}
```

`@WebMvcTest(OrderController.class)` loads the selected controller and MVC infrastructure without starting a server. `@MockitoBean` supplies the `OrderSamples` collaborator, so the test controls the order returned by the GET endpoint. Because this test depends on custom Jackson configuration, it imports `JacksonConfig`. That mirrors a common real-world slice-test rule: if the web behavior depends on a focused configuration class, include that class in the slice.

The GET test checks the response JSON at the paths that matter:

- `placedAt` is an ISO-8601 instant.
- `deliverBy` is an ISO date.
- `total` is the custom money string.
- `status` is the enum name.

The POST test sends the same shape that clients would send. If `MoneyDeserializer` is not registered, the request fails before the controller method runs. If it is registered, the echoed response writes `total` back as `"12.50 USD"`.

This kind of test is more useful than a direct unit test of the controller method. Calling `create(order)` in plain Java would skip request body parsing, enum conversion, Java time parsing, content type handling, and response serialization. The risk in this chapter lives in the web boundary, so the test goes through Spring MVC.

Run the tests from the chapter project:

```bash
./mvnw -q -B test
```

## Run It

Start the application:

```bash
./mvnw spring-boot:run
```

Read the sample order:

```bash
curl http://localhost:8080/orders/99
```

Post the same JSON shape back:

```bash
curl -i -X POST http://localhost:8080/orders \
  -H 'Content-Type: application/json' \
  -d '{
    "id": 99,
    "placedAt": "2026-05-09T10:15:30Z",
    "deliverBy": "2026-05-12",
    "total": "12.50 USD",
    "status": "PAID",
    "items": [
      { "sku": "coffee-250g", "qty": 2 },
      { "sku": "filter-100", "qty": 1 }
    ]
  }'
```

The response body should use the same shape. That is the key contract: clients do not care that Java uses `Instant`, `LocalDate`, `BigDecimal`, records, and enums internally. They care that the HTTP API has stable JSON.

Chapter 8 builds on this by adding Bean Validation. Jackson can tell whether JSON can become an `Order`; validation will tell whether that order is acceptable for the application.
