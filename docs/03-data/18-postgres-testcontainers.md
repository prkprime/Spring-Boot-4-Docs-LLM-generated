# Postgres + Testcontainers + Flyway

Chapter 17 used H2 for one reason: it let us learn the JPA shape without asking you to install a database first. That was useful for a first pass through entities, repositories, services, and controllers. It is not enough once database behavior matters.

H2 is not Postgres. The SQL dialect is different, identity columns and sequences behave differently, date and time handling can surprise you, JSON support is not the same, and locking behavior is not something you want to discover in production. The moment you care about migrations, indexes, JSON columns, case-insensitive text, transaction isolation, or query plans, your tests need the real database engine.

This chapter moves the sample application to Postgres and keeps it easy to run by using Testcontainers. Tests start a real Postgres container, Spring Boot connects the application to it, Flyway applies the schema, and Hibernate validates that the entity mapping matches the database. No H2. No generated schema. No fake dialect.

The API is still intentionally small:

```text
GET    /authors
GET    /authors/{id}
POST   /authors
DELETE /authors/{id}
```

The important change is underneath the API: Postgres is the database, Flyway owns the schema, and every test talks to the same kind of database the application will use outside tests.

## Testcontainers In 60 Seconds

Testcontainers starts real services in Docker for the lifetime of a test run. Instead of installing Postgres locally, choosing a port, creating a database, and cleaning it between tests, the test asks for a `postgres:16-alpine` container. Testcontainers starts it, waits until it is ready, exposes the JDBC URL, and stops it when the test JVM is finished.

The old Spring testing pattern looked like this:

```java
static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

@DynamicPropertySource
static void databaseProperties(DynamicPropertyRegistry registry) {
	postgres.start();
	registry.add("spring.datasource.url", postgres::getJdbcUrl);
	registry.add("spring.datasource.username", postgres::getUsername);
	registry.add("spring.datasource.password", postgres::getPassword);
}
```

That works, but it makes every test class responsible for wiring container details into Spring properties. The better Spring Boot pattern arrived in Spring Boot 3.1 and is fully supported in Spring Boot 4: declare the container as a bean and annotate it with `@ServiceConnection`.

!!! note "New since Spring Boot 3.1"
    Prefer `@ServiceConnection` for Testcontainers-backed infrastructure. Spring Boot understands common container types, including Postgres, and automatically contributes the right connection properties for auto-configuration.

The test configuration for this chapter is small:

```java
{% include-markdown "../../code/18-postgres-testcontainers/maven/src/test/java/dev/springboot4docs/ch_18_postgres_testcontainers/TestcontainersConfiguration.java" comments=false %}
```

`@TestConfiguration` keeps this bean out of the production application. Tests opt in with `@Import(TestcontainersConfiguration.class)`. `@ServiceConnection` tells Spring Boot that this container supplies a service connection. Because the bean is a `PostgreSQLContainer`, Boot configures the datasource from the container instead of using the normal `spring.datasource.url` in `application.yml`.

That last point is what makes the arrangement pleasant. The application can keep a normal local-development datasource in `application.yml`, while tests get an isolated datasource without profiles, property files, or hand-written registry code. The container object remains visible as a Spring bean, so Boot can manage lifecycle and connection details as part of the test application context.

## Flyway Owns The Schema

Chapter 17 used `ddl-auto: create-drop`, which asked Hibernate to generate the schema for an in-memory database. That is fine for a teaching chapter and not a migration strategy. Real schema changes should be named, reviewed, versioned, and applied in order.

Flyway does that with migration files. By default, Spring Boot looks under `src/main/resources/db/migration/`. Files follow this naming convention:

```text
V1__create_author.sql
V2__seed_authors.sql
```

The version comes first, then two underscores, then a descriptive name. Flyway runs migrations in version order and records what happened in a database table named `flyway_schema_history`. On the next startup, Flyway checks that history table and applies only migrations that have not run yet.

The application configuration says exactly how schema ownership works:

```yaml
{% include-markdown "../../code/18-postgres-testcontainers/maven/src/main/resources/application.yml" comments=false %}
```

The datasource points at a local Postgres for normal `spring-boot:run` usage. Tests do not use that URL because `@ServiceConnection` replaces it with the container connection. The key setting is `ddl-auto: validate`. Hibernate must not create or mutate the schema. Flyway writes the schema; Hibernate validates the mapping against it.

This gives you a useful startup failure mode. If a field is added to the entity but no migration adds the column, Hibernate validation fails. If a migration changes a column in a way the entity no longer matches, validation fails. That is much better than letting Hibernate silently change the database for you during development and then discovering later that production depends on SQL you never reviewed.

!!! warning "Do not mix ownership"
    Avoid letting Hibernate update a schema that Flyway manages. In this chapter, Flyway applies SQL migrations and Hibernate validates. That split keeps application startup honest without hiding schema changes in generated DDL.

The first migration creates the table:

```sql
{% include-markdown "../../code/18-postgres-testcontainers/maven/src/main/resources/db/migration/V1__create_author.sql" comments=false %}
```

The id column uses `BIGINT GENERATED ALWAYS AS IDENTITY`. That is Postgres-native identity syntax and maps cleanly to `GenerationType.IDENTITY`. The `bio` column uses `TEXT`, which is a normal Postgres choice for unconstrained prose. `created_at` uses `TIMESTAMPTZ`, so timestamps are stored as instants rather than ambiguous local date-times.

There is one deliberate absence in this migration: no Hibernate-generated fallback. If the SQL is wrong, the application should fail early. In a Flyway application, table names, constraints, indexes, and column types live in migrations. Entity annotations document the Java mapping, but they are not the authoritative DDL.

The second migration gives the running app and integration tests a few rows:

```sql
{% include-markdown "../../code/18-postgres-testcontainers/maven/src/main/resources/db/migration/V2__seed_authors.sql" comments=false %}
```

Seed data is useful in a follow-along guide because `GET /authors` immediately returns something. In production systems, be deliberate about seed migrations: reference data is a good fit, demo data usually is not.

## The Entity

The entity looks familiar from chapter 17, with one new field:

```java
{% include-markdown "../../code/18-postgres-testcontainers/maven/src/main/java/dev/springboot4docs/ch_18_postgres_testcontainers/Author.java" comments=false %}
```

The class still uses Jakarta Persistence imports. Spring Boot 4 and Spring Framework 7 are on the Jakarta generation of APIs, so the persistence annotations come from `jakarta.persistence.*`.

The id mapping is intentionally aligned with the migration:

```java
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;
```

This chapter uses identity because the SQL migration declares an identity column. Postgres sequences are also common, and later chapters can lean on them when batching and allocation sizes matter. The lesson here is simpler: the entity and migration must agree.

The `bio` field is mapped with `columnDefinition = "text"` because the migration uses Postgres `TEXT`. That small database-specific hint is acceptable here because the whole chapter is Postgres-specific. Once you stop pretending every database behaves the same, you can model the database you actually run.

`createdAt` is still set in `@PrePersist`. The database column is non-null, and application-created rows get an `Instant` before insert. The seed migration uses `now()` for its rows.

You could also choose a database default for `created_at`. That is a valid design, but it changes where the value becomes visible to Java. This chapter keeps the same entity callback from chapter 17 so the JPA object has the value as soon as Hibernate inserts it. The important part is that the Java mapping and SQL column type agree.

## Repository, Service, Controller

The repository remains a Spring Data JPA interface:

```java
{% include-markdown "../../code/18-postgres-testcontainers/maven/src/main/java/dev/springboot4docs/ch_18_postgres_testcontainers/AuthorRepository.java" comments=false %}
```

The derived query methods and JPQL query are unchanged from the previous chapter. What changed is the database underneath them. `findByNameContainingIgnoreCase` is no longer being checked against H2's interpretation of case-insensitive matching. The test now proves the query against Postgres.

That difference compounds as the application grows. A repository method that looks innocent in Java can generate SQL that depends on collation, function support, identifier quoting, pagination syntax, or transaction semantics. Running it against Postgres while it is still small keeps those details visible.

The service stays thin:

```java
{% include-markdown "../../code/18-postgres-testcontainers/maven/src/main/java/dev/springboot4docs/ch_18_postgres_testcontainers/AuthorService.java" comments=false %}
```

Class-level `@Transactional(readOnly = true)` covers reads, and write methods opt into normal transactions. The service creates an `Author` with `name`, `email`, and `bio`; the database supplies the id and the entity callback supplies `createdAt`.

The controller is also the same small REST controller, now accepting `bio` in the request:

```java
{% include-markdown "../../code/18-postgres-testcontainers/maven/src/main/java/dev/springboot4docs/ch_18_postgres_testcontainers/AuthorController.java" comments=false %}
```

This chapter still returns entities directly to keep focus on the database setup. DTOs, validation, richer error responses, and pagination are handled elsewhere in the guide.

## Repository Integration Test

The repository test is still a JPA slice, but it no longer uses an embedded database:

```java
{% include-markdown "../../code/18-postgres-testcontainers/maven/src/test/java/dev/springboot4docs/ch_18_postgres_testcontainers/AuthorRepositoryIntegrationTest.java" comments=false %}
```

`@DataJpaTest` loads the JPA slice. `@Import(TestcontainersConfiguration.class)` adds the Postgres container bean. `@AutoConfigureTestDatabase(replace = NONE)` tells the slice not to replace the datasource with an embedded test database. There is no H2 dependency in this chapter, and that is deliberate.

Spring Boot starts the container, creates a datasource from the container connection details, and initializes the test database. Flyway runs before the JPA test uses the repository, so the `author` table and seed rows already exist. Hibernate then validates the entity mapping because `ddl-auto` is set to `validate`.

That gives the test a much stronger signal than the H2 version. It proves that the migration runs, the Postgres column types are usable, the entity maps to the real table, and the repository queries work against the actual dialect.

The test still has the normal benefits of a JPA slice. It does not start the web layer, and test methods are transactional by default. Rows inserted during a method are rolled back when that method finishes. The Flyway seed rows remain the shared baseline because they were created before the test transaction.

## HTTP Integration Test

The full integration test starts the application on a random port and talks to it over HTTP:

```java
{% include-markdown "../../code/18-postgres-testcontainers/maven/src/test/java/dev/springboot4docs/ch_18_postgres_testcontainers/AuthorIntegrationTest.java" comments=false %}
```

This is the same testing style as chapter 17, but the database is no longer an in-memory shortcut. `RestTestClient` sends real HTTP requests to the running application. The application uses Spring MVC, the service, the repository, Flyway migrations, Hibernate validation, the Postgres driver, and a real Postgres container.

The first test confirms that Flyway seed data is visible through `GET /authors`. The second test performs an end-to-end create, read, delete, and read-again flow. The created id is captured from the response instead of assuming `1`, because the seed migration already inserted rows.

This is the test that catches wiring mistakes across layers. If the controller accepts the wrong JSON shape, the service forgets a field, the repository mapping is invalid, Flyway did not run, or Boot did not connect to the container, this test fails through the same public API a client would use.

## Local Development

For normal application runs, `application.yml` expects a local Postgres:

```bash
docker run --name sb4docs-postgres --rm \
  -e POSTGRES_DB=sb4docs \
  -e POSTGRES_USER=sb4 \
  -e POSTGRES_PASSWORD=sb4 \
  -p 5432:5432 \
  postgres:16-alpine
```

Then run the app:

```bash
./mvnw spring-boot:run
```

Flyway runs on startup in this mode too. If the database is empty, `V1__create_author.sql` creates the table and `V2__seed_authors.sql` inserts the sample authors. If the database already has those migrations recorded in `flyway_schema_history`, Flyway leaves them alone. That is the same lifecycle you use in tests, just pointed at your local database.

The test source also includes a `TestApplication`:

```java
{% include-markdown "../../code/18-postgres-testcontainers/maven/src/test/java/dev/springboot4docs/ch_18_postgres_testcontainers/TestApplication.java" comments=false %}
```

That class starts the real application and imports the Testcontainers configuration. It is handy when you want a disposable Postgres without running Docker commands yourself:

```bash
./mvnw spring-boot:test-run
```

With the app running, try the API:

```bash
curl http://localhost:8080/authors

curl -i -X POST http://localhost:8080/authors \
  -H 'Content-Type: application/json' \
  -d '{"name":"Becky Chambers","email":"becky.local@example.com","bio":"Author of optimistic science fiction."}'
```

## What Postgres Unlocks

Using Postgres in tests lets you lean on Postgres features without apologizing for them. `TIMESTAMPTZ` is already in this chapter. Later chapters can use `JSONB`, `citext`, partial indexes, expression indexes, advisory locks, full-text search, and realistic transaction behavior. Those are not details you want approximated by a different database during tests.

The point is not that every test must be slow or broad. You can still write narrow unit tests and MVC slice tests where they make sense. But persistence tests should use the database you actually support. By 2026, Spring Boot plus Testcontainers plus `@ServiceConnection` is the canonical setup for that.

Run the chapter tests from the Maven project:

```bash
./mvnw -q -B test
```

On the first run, Docker may need to pull `postgres:16-alpine`, so expect the build to take longer than an ordinary unit-test run. After the image is cached, startup is usually much faster.

Chapter 19 builds on this foundation by adding relationships and showing how N+1 queries appear when entity associations meet real database access.
