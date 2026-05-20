# Testing II — Slice Tests

Chapter 5 introduced the testing toolbox: JUnit, AssertJ, Mockito, `MockMvcTester`, `RestTestClient`, and `@SpringBootTest`. This chapter uses that toolbox more deliberately. Instead of asking every test to start the whole application, we choose the smallest test shape that can prove the behavior we care about.

The application is a tiny notes API. It has one JPA entity, one repository, one service, and one controller:

```xml
{% include-markdown "../../code/42-testing-slices/maven/pom.xml" comments=false %}
```

The main dependencies are `spring-boot-starter-webmvc`, `spring-boot-starter-data-jpa`, and H2. The test side uses the MVC and JPA test starters. The Surefire `argLine` is the same setting used in earlier chapters: Mockito runs as a Java agent so tests work on modern JDKs where self-attachment is no longer something to rely on.

```java
{% include-markdown "../../code/42-testing-slices/maven/src/main/java/dev/springboot4docs/ch_42_testing_slices/Note.java" comments=false %}
```

```java
{% include-markdown "../../code/42-testing-slices/maven/src/main/java/dev/springboot4docs/ch_42_testing_slices/NoteRepository.java" comments=false %}
```

```java
{% include-markdown "../../code/42-testing-slices/maven/src/main/java/dev/springboot4docs/ch_42_testing_slices/NoteService.java" comments=false %}
```

```java
{% include-markdown "../../code/42-testing-slices/maven/src/main/java/dev/springboot4docs/ch_42_testing_slices/NoteController.java" comments=false %}
```

The point is not the notes domain. The point is the testing shape around it.

## The Pyramid Again

A useful Spring Boot test suite still looks like a pyramid:

```text
           /\
          /  \        full application tests
         /____\       fewer, slower, broad confidence
        /      \
       / slice  \     framework integration for one layer
      /__________\
     /            \
    /    unit      \  many, fast, precise tests
   /________________\
```

Start at the bottom. A unit test creates the class directly and passes mocks or fakes to its constructor. It is the fastest way to test decisions in your own code.

Move up when the framework matters. If the behavior depends on MVC request mapping, JSON conversion, repository queries, transaction rollback, validation, or an auto-configured test helper, a slice test is usually the right middle layer.

Use full application tests sparingly. They prove that the real wiring works across layers, but they are slower, less focused, and more likely to fail for broad environmental reasons.

## The Slice Catalog

`@WebMvcTest` loads the Spring MVC layer around selected controllers. It does not load your JPA repositories or ordinary service beans. It does auto-configure MVC infrastructure such as handler mappings, JSON conversion, validators, `@ControllerAdvice`, and `MockMvc`/`MockMvcTester`. In Spring Boot 4 the annotation is `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`.

`@DataJpaTest` loads the JPA layer. It gives you repositories, an `EntityManager`, transactions, and an embedded database when one is available. Each test method runs in a transaction that rolls back by default. It does not load controllers. In Spring Boot 4 the annotation is `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`.

`@JsonTest` loads Jackson and the JSON test support without starting an MVC context. It auto-configures `JacksonTester<T>`, which is ideal for small serialization and deserialization round trips. In Spring Boot 4.0.6 this annotation is still `org.springframework.boot.test.autoconfigure.json.JsonTest`.

`@WebFluxTest` is the reactive analogue of `@WebMvcTest`. Use it for WebFlux controllers and functional handlers. This guide is on the Spring MVC path, so we only name it here.

`@JdbcTest` is like the data slice without JPA. Use it when the code talks directly through `JdbcTemplate`, `JdbcClient`, or lower-level SQL helpers.

`@RestClientTest` is for outbound HTTP clients. It configures a client-side slice and a mock server so you can test "my service calls another service correctly" without starting the other service.

Store slices such as `@DataRedisTest`, `@DataMongoTest`, `@DataJdbcTest`, and similar annotations follow the same pattern: load the infrastructure for one persistence technology, leave the rest of the application out.

Slices are intentionally narrow. When a slice does not include a bean you need, you have two normal options. Use `@MockitoBean` to replace a collaborator with a Mockito mock, or use `@Import(ServiceX.class)` when the real bean belongs in the slice for this test. If you want real security behavior in a web slice, import the relevant `SecurityConfig` explicitly. `@MockitoBean` is the modern Spring Framework annotation from `org.springframework.test.context.bean.override.mockito.MockitoBean`; the older Boot `@MockBean` style is deprecated. Use `@MockitoSpyBean` when you need a spy instead of a mock.

Here are the imports to notice in the sample:

| Job | Type | Import |
| --- | --- | --- |
| MVC slice | `@WebMvcTest` | `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` |
| JPA slice | `@DataJpaTest` | `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest` |
| JSON slice | `@JsonTest` | `org.springframework.boot.test.autoconfigure.json.JsonTest` |
| Spring mock bean | `@MockitoBean` | `org.springframework.test.context.bean.override.mockito.MockitoBean` |
| MVC test client | `MockMvcTester` | `org.springframework.test.web.servlet.assertj.MockMvcTester` |
| Full-context test client | `RestTestClient` | `org.springframework.test.web.servlet.client.RestTestClient` |

The package moves matter because older examples on the internet often show Spring Boot 3 imports. In this guide, use the Boot 4 imports from the code, not whatever your editor auto-completes from a stale snippet.

## Pure Unit

The service unit test uses no Spring annotation:

```java
{% include-markdown "../../code/42-testing-slices/maven/src/test/java/dev/springboot4docs/ch_42_testing_slices/NoteServiceUnitTest.java" comments=false %}
```

This test is plain Java plus Mockito. The repository is a mock field managed by `MockitoExtension`, and each test creates `NoteService` with `new NoteService(this.notes)`.

That keeps failures precise. If `createsANoteThroughTheRepository` fails, the problem is in `NoteService.create(...)` or in the expectations in that test. There is no application context, no database, no MVC layer, and no component scanning involved.

Use this shape when the behavior is in your own class and the framework would only add noise. Services, mappers, pricing rules, permission checks, parsers, and small domain policies usually deserve many tests at this layer.

The tradeoff is that Mockito does not prove persistence behavior. The mock returns exactly what the test tells it to return. That is useful when the service branch is the subject, but it says nothing about table mappings, generated SQL, or whether a derived repository method can be parsed. That is why the next test exists.

## JPA Slice

The repository test loads real Spring Data JPA with H2:

```java
{% include-markdown "../../code/42-testing-slices/maven/src/test/java/dev/springboot4docs/ch_42_testing_slices/NoteRepositoryTest.java" comments=false %}
```

`@DataJpaTest` is the right tool because the behavior depends on Spring Data query creation and Hibernate mapping. A pure unit test cannot prove that `findByTitleContainingIgnoreCase(...)` becomes the query we expect.

Notice the rollback test. The method asserts that the repository starts empty. That works because each `@DataJpaTest` method runs in a transaction that rolls back after the method finishes. The inserts from `searchesByTitleIgnoringCase` do not leak into the next test.

By default, this slice replaces the application datasource with an embedded one when an embedded database is on the classpath. That is convenient here because H2 is part of the sample. When you intentionally want the real configured datasource, add `@AutoConfigureTestDatabase(replace = NONE)` and make sure the database lifecycle is controlled by the test setup.

This is also the layer where you should catch accidental repository regressions before a full application test ever runs. If a field rename breaks an entity mapping, or a repository method name no longer matches a property, the JPA slice gives you a small failing context with a narrow explanation.

## MVC Slice

The controller test loads MVC around `NoteController` and mocks the service:

```java
{% include-markdown "../../code/42-testing-slices/maven/src/test/java/dev/springboot4docs/ch_42_testing_slices/NoteControllerTest.java" comments=false %}
```

`@WebMvcTest(NoteController.class)` says that the controller is the subject. The test gets a `MockMvcTester`, which drives Spring MVC through the mock servlet infrastructure. No server port is opened.

The controller constructor needs `NoteService`, but a web slice does not include the real service bean. `@MockitoBean` registers a Mockito mock in the test application context, so MVC can instantiate the controller while the test controls the service behavior.

This is where route behavior belongs. The test checks `GET /notes`, JSON shape, the `201 Created` status and `Location` header for `POST /notes`, the `404` mapping from `NoteNotFoundException`, and the `204` response for delete. The status assertions use the Spring Framework 7 AssertJ style: `.hasStatus(201)`, `.hasStatus(404)`, and `.hasStatus(204)`.

A common footgun is assuming a web slice has your whole security setup. It does not automatically mean "my production filter chain is active in the same way." If the behavior under test depends on a real `SecurityFilterChain`, import the security configuration into the slice and provide any supporting beans it needs.

Keep the mock boundary honest. The controller test should not restate every service test through stubbing. It should stub just enough service behavior to drive MVC paths: happy response, not found response, validation response, or header behavior. When a controller test becomes a long mock script, it is usually trying to test too much at once.

## JSON Slice

The JSON test loads Jackson without MVC:

```java
{% include-markdown "../../code/42-testing-slices/maven/src/test/java/dev/springboot4docs/ch_42_testing_slices/NoteJsonTest.java" comments=false %}
```

`JacksonTester<T>` gives a small, readable API for serialization tests. Here the request record is parsed from JSON, the response record is written to JSON, and one test proves a response can round-trip through write and parse.

This is narrower than `@WebMvcTest`. It will not test request mapping, status codes, content negotiation, or exception handlers. That is the point. Use `@JsonTest` when the question is "does this type serialize the way our API contract expects?" and nothing else.

This slice becomes especially useful when you add custom Jackson modules, `@JsonComponent` serializers, naming strategies, date/time formats, or polymorphic DTOs. Those are JSON concerns, not web concerns.

The JSON slice is also a useful place to protect backwards compatibility. If a client depends on `title` staying `title`, not `noteTitle`, a tiny `@JsonTest` can lock that contract without starting MVC, JPA, or a server.

## Full Application

The final test loads the application context and calls the API through `RestTestClient`:

```java
{% include-markdown "../../code/42-testing-slices/maven/src/test/java/dev/springboot4docs/ch_42_testing_slices/NoteFullStackTest.java" comments=false %}
```

This is not a slice. `@SpringBootTest` loads the real application: controller, service, repository, JPA, Jackson, transactions, and the H2 datasource. `@AutoConfigureRestTestClient` supplies `RestTestClient`, which gives the same request/response style we use for HTTP-facing tests.

In an unconstrained environment you can also run this style with `webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT` to start the embedded server on an available port. This sample binds the client through Spring's servlet test infrastructure so it can run in restricted local and CI environments that do not allow opening a server socket. The application coverage is still broad: the request crosses MVC, JSON conversion, service code, repository code, and the database.

The first test creates a note and then lists notes. The second starts with a real repository save, updates through the API, deletes through the API, and confirms the resource is gone. This is a seam test: it verifies that layers cooperate. It is intentionally not a replacement for the smaller unit, repository, MVC, and JSON tests above.

The setup deletes repository contents before each method because a full application test does not get the same automatic rollback behavior as a `@DataJpaTest` method. If a full test writes shared state, clean that state deliberately. Hidden ordering between integration tests is one of the fastest ways to make CI unreliable.

Read the five classes as a comparison, not as a mandate to write five tests for every feature:

| Test | Loads Spring? | Real database? | Best signal |
| --- | --- | --- | --- |
| `NoteServiceUnitTest` | No | No | Service decisions |
| `NoteRepositoryTest` | JPA slice | H2 | Mapping and query behavior |
| `NoteControllerTest` | MVC slice | No | Routes, statuses, JSON shape |
| `NoteJsonTest` | JSON slice | No | Serialization contract |
| `NoteFullStackTest` | Full context | H2 | Cross-layer wiring |

## How To Choose

Use this decision tree when adding a test:

1. Can the behavior be tested by constructing one class directly? Write a unit test.
2. Does the behavior depend on a framework layer? Write the narrow slice for that layer.
3. Does the behavior prove an important user or system seam across layers? Add a full `@SpringBootTest`.

For a controller change, start with a controller unit test only if there is meaningful logic in the controller itself. Most controller changes are better served by `@WebMvcTest`, because the real risk is route mapping, request binding, validation, status codes, and JSON response shape.

For repository changes, prefer `@DataJpaTest`. Derived queries, JPQL, entity mappings, generated identifiers, flush behavior, and transaction boundaries are not plain Java behavior.

For business logic, prefer a unit test first. Add a slice only when the framework integration is part of the behavior, and add a full application test only at important seams such as signup, checkout, payment capture, report generation, or cross-service flows.

## Footguns

`@WebMvcTest` is not "the whole web application, but faster." It is a web slice. If your test depends on filters, security rules, custom MVC configuration, or non-web beans, import what you need intentionally.

`@DataJpaTest` swaps the datasource for an embedded one by default. That is excellent for fast local persistence tests, but it can hide database-specific behavior. Chapter 43 will move to Testcontainers when the dialect matters.

`@SpringBootTest` contexts are cached by configuration. That cache is why repeated integration tests can be tolerable. It is also why many combinations of `@ActiveProfiles`, dynamic properties, imported test configs, and mock beans can slow CI: each distinct configuration needs its own cached context.

Run this chapter from the Maven directory:

=== "Maven"
    ```bash
    ./mvnw test
    ```

=== "Gradle"
    ```bash
    ./gradlew test
    ```

Chapter 43 keeps the pyramid idea but swaps H2 for real infrastructure with Testcontainers.
