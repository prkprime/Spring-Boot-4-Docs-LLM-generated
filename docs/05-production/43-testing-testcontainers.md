# Testing III — Testcontainers

Chapter 42 showed the testing pyramid with an in-memory database. That is useful for learning the shape of slice tests and full application tests, but it has a limit: H2 is not Postgres. It does not have the same SQL dialect, the same indexes, the same type system, the same transaction behavior, or the same operational edges.

Testcontainers gives us a better answer when the real service matters. The test starts a Docker container, Spring Boot connects to it, the test runs, and the container is stopped when the JVM is done. For this chapter the service is Postgres, but the same idea applies to Redis, MongoDB, RabbitMQ, Kafka, Elasticsearch, and many other systems.

The application is intentionally small: one `Customer` entity, one repository, one service, and one MVC controller. The point is the testing setup around the real database.

```xml
{% include-markdown "../../code/43-testing-testcontainers/maven/pom.xml" comments=false %}
```

The main application dependencies are Spring MVC, Spring Data JPA, and the PostgreSQL JDBC driver. The test dependencies are the JPA test starter, the MVC test starter, `spring-boot-testcontainers`, and the Testcontainers PostgreSQL module.

`spring-boot-testcontainers` is the Spring Boot integration layer. It knows how to inspect container beans marked with `@ServiceConnection` and contribute the right connection details to the application context.

That dependency is the difference between "Testcontainers starts something" and "Spring Boot knows how to use what Testcontainers started." Without it, you can still use containers, but you must publish the connection properties yourself. With it, common infrastructure becomes normal Spring configuration: define a container bean, mark it as a service connection, import that configuration into the test, and let Boot do the binding.

This is also why the sample has no `spring.datasource.url` in `application.properties`. The JDBC URL is not stable. Docker chooses a host port at runtime, and Testcontainers exposes that port to the JVM. A hard-coded URL would fight the tool. The container is the source of truth during tests.

The sample does set one test-only JPA property:

```properties
{% include-markdown "../../code/43-testing-testcontainers/maven/src/test/resources/application.properties" comments=false %}
```

That tells Hibernate to create and drop the `customer` table for this disposable test database. Use this only for the chapter-sized learning example. In production, use a migration tool such as Flyway or Liquibase so schema changes are versioned, reviewed, and repeatable.

## The Sample API

The domain is a customer record with an id, an email address, and a creation timestamp:

```java
{% include-markdown "../../code/43-testing-testcontainers/maven/src/main/java/dev/springboot4docs/ch_43_testing_testcontainers/Customer.java" comments=false %}
```

The repository uses a normal Spring Data JPA interface:

```java
{% include-markdown "../../code/43-testing-testcontainers/maven/src/main/java/dev/springboot4docs/ch_43_testing_testcontainers/CustomerRepository.java" comments=false %}
```

The service keeps the controller away from direct persistence calls:

```java
{% include-markdown "../../code/43-testing-testcontainers/maven/src/main/java/dev/springboot4docs/ch_43_testing_testcontainers/CustomerService.java" comments=false %}
```

The controller exposes a compact HTTP surface:

```java
{% include-markdown "../../code/43-testing-testcontainers/maven/src/main/java/dev/springboot4docs/ch_43_testing_testcontainers/CustomerController.java" comments=false %}
```

The not-found exception is deliberately plain:

```java
{% include-markdown "../../code/43-testing-testcontainers/maven/src/main/java/dev/springboot4docs/ch_43_testing_testcontainers/CustomerNotFoundException.java" comments=false %}
```

There is nothing Testcontainers-specific in these application classes. That is the first important design point. The production application still looks like an ordinary Spring Boot app. The real database appears in the test configuration.

That separation matters in real projects. Do not let test infrastructure leak into application code just to make tests easier. The application should declare that it needs a datasource. The test should decide whether that datasource comes from H2, a Dockerized Postgres, a local developer database, or a CI-provided service.

## Service Connections

In older Testcontainers examples you will often see `@DynamicPropertySource`:

```java
@DynamicPropertySource
static void postgresProperties(DynamicPropertyRegistry registry) {
	registry.add("spring.datasource.url", postgres::getJdbcUrl);
	registry.add("spring.datasource.username", postgres::getUsername);
	registry.add("spring.datasource.password", postgres::getPassword);
}
```

That still works, and it is still useful for containers that Spring Boot does not understand directly. But for common services, Spring Boot 3.1 introduced a better pattern: `@ServiceConnection`. Spring Boot 4 continues that model.

`@ServiceConnection` is a marker annotation on a container bean. When Boot sees a supported container type, it creates the matching connection details automatically. For `PostgreSQLContainer`, Boot contributes the datasource URL, username, password, and driver information that JPA needs. The application does not need to know which random host port Docker assigned.

The old pattern was explicit and flexible. The new pattern is declarative and harder to get wrong. That is the tradeoff:

| Pattern | Best for | Cost |
| --- | --- | --- |
| `@ServiceConnection` | Supported services such as JDBC databases, Redis, MongoDB, RabbitMQ, and similar integrations | You rely on Boot's connection-details support |
| `@DynamicPropertySource` | Unsupported containers, custom protocols, or unusual property names | You must map every property yourself |

Use `@ServiceConnection` first when Spring Boot supports your service. Reach for `@DynamicPropertySource` when you are integrating something Boot cannot infer.

Here is the whole Testcontainers configuration:

```java
{% include-markdown "../../code/43-testing-testcontainers/maven/src/test/java/dev/springboot4docs/ch_43_testing_testcontainers/TestcontainersConfiguration.java" comments=false %}
```

The bean method returns `PostgreSQLContainer`, not `PostgreSQLContainer<?>`. Newer Testcontainers versions no longer need the parameterized form for this container type.

The image is pinned to `postgres:16-alpine`. Pinning matters. A moving `latest` tag can quietly change the database under your tests. The `alpine` variant is smaller, which helps local and CI startup time.

This configuration also uses a `static` field and calls `start()` once. That means every test class importing this configuration in the same Maven test JVM shares the same container instance. Spring Boot still handles the connection details, and Spring's test context cache keeps repeated context startup tolerable.

The explicit `start()` call is what makes the container JVM-shared instead of context-owned. If the bean method created a new container every time, distinct Spring test contexts could start distinct Postgres instances. That is simpler to reason about but slower when a chapter or project has many integration tests. A static container gives us one database process, while each test class remains responsible for cleaning or rolling back its own data.

This is a performance choice, not a correctness shortcut. The repository slice relies on transaction rollback. The HTTP test deletes rows before each method. If you share a container but forget data isolation, tests can pass or fail depending on execution order. That kind of hidden coupling is worse than a slower suite.

## JPA Slice With Real Postgres

The first test is a JPA slice:

```java
{% include-markdown "../../code/43-testing-testcontainers/maven/src/test/java/dev/springboot4docs/ch_43_testing_testcontainers/CustomerRepositoryIntegrationTest.java" comments=false %}
```

The imports are the details to notice:

```java
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;
```

`@DataJpaTest` loads the JPA slice: entities, repositories, Hibernate, the datasource, transaction management, and JPA test support. It does not load the MVC controller or the service layer.

`@Import(TestcontainersConfiguration.class)` adds the Postgres container bean to the slice. `@AutoConfigureTestDatabase(replace = NONE)` is essential. Without it, a data slice is allowed to replace the configured datasource with an embedded test database when one is available. In this chapter we explicitly want the datasource supplied by the container.

The test proves that the repository can save a `Customer`, flush it to Postgres, and query it by email. The second method asserts that the repository starts empty. That works because `@DataJpaTest` wraps each test method in a transaction and rolls it back afterward.

This is the sweet spot for database-specific behavior. Entity mappings, generated ids, derived query parsing, column constraints, PostgreSQL-specific SQL, and transaction behavior all belong here before they become full application test failures.

For example, a unique index, a JSONB column, a case-insensitive expression index, or a hand-written native query should not be tested only against H2. H2 compatibility modes can be useful, but they are not a contract that production Postgres will behave identically. When the database behavior is part of the feature, the test database should be the same database family.

The slice remains smaller than a full app test. There is no HTTP port, no controller, and no MVC conversion layer. If the failure is in this test, you can focus on persistence: entity annotations, repository method names, transaction behavior, or generated SQL.

## Full HTTP Path

The second test starts the whole application on a random HTTP port:

```java
{% include-markdown "../../code/43-testing-testcontainers/maven/src/test/java/dev/springboot4docs/ch_43_testing_testcontainers/CustomerHttpIntegrationTest.java" comments=false %}
```

This is not a slice. `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)` starts the embedded servlet container. `@AutoConfigureRestTestClient` provides a `RestTestClient` bound to that running application. `@Import(TestcontainersConfiguration.class)` brings in the same Postgres container setup.

The request crosses the real HTTP boundary, Spring MVC, JSON conversion, the service, Spring Data JPA, Hibernate, the PostgreSQL driver, and the Postgres container. That is a much broader assertion than the repository slice, so you should write fewer tests at this layer. Use it for important application seams, not every repository method.

Notice the cleanup in `@BeforeEach`. Unlike `@DataJpaTest`, a full application test does not automatically roll back the HTTP request transaction after the test method. The app handles each request in its own transaction. If the test writes state, clean that state deliberately so tests remain order-independent.

This test is valuable because it catches wiring mistakes. A controller can accept the wrong JSON field. A service can call the wrong repository method. JPA can fail to create the schema. Boot can fail to build a datasource. The HTTP test sees the whole path, so it is the right place for one or two representative user flows.

It is not the right place for every branch. If you add validation, test the web status and JSON error shape in a web slice. If you add a repository query, test it in a JPA slice. Keep the full HTTP tests for the paths where "all of these layers cooperate" is the behavior you need to prove.

## Local Manual Testing

Spring Boot's test-run support can also use this test configuration while you manually exercise the app:

```java
{% include-markdown "../../code/43-testing-testcontainers/maven/src/test/java/dev/springboot4docs/ch_43_testing_testcontainers/TestApplication.java" comments=false %}
```

Run it from the Maven project:

```bash
./mvnw spring-boot:test-run
```

`SpringApplication.from(Application::main)` starts from the real application. `.with(TestcontainersConfiguration.class)` adds the test-only Postgres container. The result is a normal local application with a real disposable Postgres database behind it.

That is a strong development loop. You can change the API, start the app with a database you did not have to install, and call it from another terminal:

```bash
curl -i localhost:8080/customers \
  -H 'content-type: application/json' \
  -d '{"email":"local@example.com"}'
```

The database is still managed by Testcontainers. You do not need a local Postgres service, a manually created database, or copied credentials.

## Lifecycle Choices

There are three common lifecycle shapes.

Per-method containers are the most isolated and the slowest. Each test method starts fresh infrastructure. Use this only when tests truly need total isolation at the container level.

Per-class containers are a common middle ground. One test class shares a container and cleans application data between methods.

Per-suite or JVM-shared containers are fastest for a larger test suite. This chapter's `static` field in `TestcontainersConfiguration` is that pattern. Every Spring test class that imports the same configuration in the same JVM gets the same already-started container.

For local development, you can opt into container reuse by adding `.withReuse(true)` to the container definition. It asks Testcontainers to keep the container alive beyond a single test JVM so later runs can attach faster. Reuse is opt-in on the developer machine. Add this to `~/.testcontainers.properties`:

```properties
testcontainers.reuse.enable=true
```

Use reuse for local iteration, not as a hidden CI dependency. CI should be able to start from a clean machine and still pass.

Also remember that reuse does not mean persistent application state is safe to depend on. A reused local container may still contain tables or rows from an earlier run if your schema management and cleanup allow that. Treat reuse as a startup optimization only. Tests should create the data they need and remove or roll back the data they write.

When a project grows, you can combine these lifecycle choices:

| Test type | Container shape | Data isolation |
| --- | --- | --- |
| JPA slices | Shared static container | Transaction rollback |
| Full HTTP tests | Shared static container | Explicit cleanup |
| Destructive infrastructure tests | Dedicated container | Container discard |

The expensive operation is usually starting infrastructure, not inserting a few rows. Share the infrastructure when it is safe, and isolate data at the test level.

## CI Notes

Containers add time. A warmed-up Postgres container may add only a few seconds; the first run that pulls an image can add much more. A practical CI setup caches Docker layers, uses small images such as `postgres:16-alpine`, and pins versions so builds are repeatable.

Keep the test pyramid in mind. Testcontainers is not a reason to make every test a full application test. Use unit tests for your own code, slice tests for one Spring layer, and full container-backed tests for important cross-layer paths.

On hosted CI, the main failure modes are usually environmental: Docker is not enabled, the job cannot pull images, a private registry needs credentials, or parallel jobs exhaust disk space. Make those assumptions explicit in the pipeline. If the build needs Docker, install or enable Docker in the job image. If the image comes from a registry with rate limits, cache it or mirror it deliberately.

You should also be careful with "floating" service versions. `postgres:latest` might pass today and behave differently next month. Pin to the major version you run in production, and upgrade intentionally. The tests are most useful when they track the real platform you care about.

The payoff is confidence. You stop guessing whether H2 behaved like Postgres. Your tests use the same database family as production, with the same driver and the same SQL dialect, while still being automated and disposable.

Run the chapter from the Maven directory:

```bash
./mvnw -q -B test
```

Chapter 44 continues from this production-testing foundation and moves into the next operational concern.
