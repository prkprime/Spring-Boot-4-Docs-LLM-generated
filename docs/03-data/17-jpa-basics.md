# Spring Data JPA Basics

This chapter builds the smallest useful database-backed CRUD API in the guide: an `Author` entity, a Spring Data JPA repository, a thin service, and a controller with list, read, create, and delete endpoints.

Spring Data JPA gives you a repository abstraction over JPA. You define an interface, extend `JpaRepository`, and Spring creates the runtime implementation. You get common persistence methods for free, you can add derived query methods by naming convention, and you can drop down to JPQL when a method name would become awkward.

The shape of the API is deliberately small:

```text
GET    /authors
GET    /authors/{id}
POST   /authors
DELETE /authors/{id}
```

Pagination waits until chapter 21. Validation, richer error responses, relationships, auditing, migrations, and production database testing each get their own chapter. Here we establish the vocabulary you will reuse through the rest of Part III.

!!! warning "Only H2 chapter"
    This is the only Part III chapter that uses H2. Chapter 18 moves straight to Postgres, Testcontainers, and Flyway so dialect differences, migrations, and real database behavior appear early.

## The Entity

JPA entities are mutable classes managed by the persistence provider. That is why this chapter uses a normal Java class instead of a record:

```java
{% include-markdown "../../code/17-jpa-basics/maven/src/main/java/dev/springboot4docs/ch_17_jpa_basics/Author.java" comments=false %}
```

Records are excellent for request DTOs, response DTOs, projections, and immutable values. They do not fit ordinary JPA entity rules well because JPA needs a no-argument constructor, field state it can populate, and identity that changes when a new row is inserted. The entity therefore has a protected no-arg constructor for JPA and a public constructor for application code.

The imports are all Jakarta imports:

```java
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
```

Spring Boot 4 and Spring Framework 7 are on the Jakarta EE generation of APIs. Do not copy old `javax.persistence.*` imports into this project.

`@Entity` marks the class as persistent. With no explicit table name, Hibernate maps it to a table based on the entity name. The `id` field is the primary key:

```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;
```

`@GeneratedValue` tells JPA that the database or provider creates the identifier. The common strategies are:

- `IDENTITY`: use an identity or auto-increment column. Simple, and a good fit for this H2 chapter.
- `SEQUENCE`: use a database sequence. Often preferred with Postgres because Hibernate can batch inserts more effectively.
- `AUTO`: let the provider choose for the database dialect.
- `UUID`: generate UUID identifiers for UUID-typed ids.

The `@Column` annotations are database mapping hints:

```java
@Column(nullable = false, length = 120)
private String name;

@Column(nullable = false, unique = true)
private String email;
```

`nullable = false`, `unique = true`, `length = 120`, and `name = "created_at"` affect generated DDL when Hibernate creates the schema. They also document the persistence contract next to the field. They are not a replacement for request validation at the HTTP boundary. A null name may still reach the database and fail as a database constraint violation; chapter 8 covered boundary validation, and later chapters combine those ideas with persistence.

The `createdAt` field is set server-side:

```java
@PrePersist
void prePersist() {
	this.createdAt = Instant.now();
}
```

`@PrePersist` runs before the entity is first inserted. That keeps clients from choosing their own creation time. Chapter 23 replaces this manual callback with Spring Data auditing, which is the better fit once more entities need `createdAt`, `updatedAt`, or `createdBy`.

One more entity detail is easy to miss: Lombok-generated accessors do not mean every field should be changed by every caller. JPA needs a way to populate state, and frameworks often expect JavaBean-style methods. Your application still owns the rules. In this chapter the controller never accepts `id` or `createdAt` from the request. The database chooses the id, and the entity callback chooses the creation time.

When an entity is loaded inside a transaction, it is managed by the persistence context. Changes to managed entities can be detected and flushed to the database when the transaction commits. This chapter does not add an update endpoint because the first lesson is the repository shape, but that managed-state idea is central to JPA. Later chapters use it when relationships, transactions, and dirty checking become visible.

## The Repository

The repository is just an interface:

```java
{% include-markdown "../../code/17-jpa-basics/maven/src/main/java/dev/springboot4docs/ch_17_jpa_basics/AuthorRepository.java" comments=false %}
```

`JpaRepository<Author, Long>` says this repository manages `Author` entities with `Long` ids. That inheritance gives you a large base API, including:

- `save(entity)` to insert or update
- `findAll()` to list rows
- `findById(id)` to read one row as an `Optional`
- `deleteById(id)` and `delete(entity)` to remove rows
- `count()` to count rows
- `existsById(id)` to check existence

The two derived queries are read by Spring Data from their method names:

```java
Optional<Author> findByEmail(String email);

List<Author> findByNameContainingIgnoreCase(String fragment);
```

The naming convention is literal. `findByEmail` means where `email = ?`. `findByNameContainingIgnoreCase` means a case-insensitive contains match against `name`. You will commonly see patterns such as `findByEmail`, `findByNameAndEmail`, `findByCreatedAtAfter`, `findByNameContainingIgnoreCase`, and `existsByEmail`.

Derived names are best when the query reads naturally. When the name stops being readable, use `@Query`.

This chapter includes one JPQL query:

```java
@Query("select a from Author a where lower(a.name) like lower(concat(:prefix, '%')) order by a.name")
List<Author> findByNamePrefix(String prefix);
```

JPQL talks in entity names and entity fields: `Author`, `a.name`, and `a.email`, not table and column names. Prefer JPQL over native SQL while the query is still about your domain model. Use native SQL when you need a database-specific feature, a vendor function, or a query shape JPQL cannot express cleanly.

The repository methods return domain-oriented types. `findById` and `findByEmail` return `Optional<Author>` because the row may not exist. Collection queries return `List<Author>` because zero matches is still a successful query. Those return types keep absence explicit and prevent the controller from pretending every lookup succeeds.

`save` is the one method name that hides the most behavior. For a new entity with no id, it inserts. For an existing entity, it can update. That is convenient, but it is not a reason to blur create and update semantics in your HTTP API. The controller still uses `POST /authors` for create, and later chapters will use explicit routes for updates.

## Service and Controller

The service is intentionally thin:

```java
{% include-markdown "../../code/17-jpa-basics/maven/src/main/java/dev/springboot4docs/ch_17_jpa_basics/AuthorService.java" comments=false %}
```

The class-level `@Transactional(readOnly = true)` covers reads. The write methods override it with plain `@Transactional`. That transaction boundary matters more as soon as a method changes multiple rows, touches lazy relationships, publishes events, or combines reads and writes. Here it establishes the habit without hiding much logic.

The controller is a normal Spring MVC REST controller:

```java
{% include-markdown "../../code/17-jpa-basics/maven/src/main/java/dev/springboot4docs/ch_17_jpa_basics/AuthorController.java" comments=false %}
```

`GET /authors` returns all authors. That is acceptable for a tiny chapter database, but not for a real unbounded table. Chapter 21 introduces pagination and sorting.

`GET /authors/{id}` returns `200 OK` with the author when `findById` succeeds, or `404 Not Found` when the repository returns `Optional.empty()`.

`POST /authors` reads a small request record, creates an entity through the service, and returns `201 Created`. The `Location` header points at the new resource:

```java
URI location = ServletUriComponentsBuilder.fromCurrentRequest()
		.path("/{id}")
		.buildAndExpand(saved.getId())
		.toUri();
```

The request type is a record because it is not a JPA entity. It is just the JSON shape the controller accepts. Keeping the entity as a class and the request as a record is a useful split: persistence state on one side, HTTP input on the other.

`DELETE /authors/{id}` delegates to the service and returns `204 No Content`. There is no response body because the resource is gone.

This controller returns entities directly to keep the example compact. Many production APIs return response DTOs instead. DTOs let you hide internal fields, rename JSON properties without changing persistence mapping, combine several entities into one response, or avoid serializing lazy relationships accidentally. For this first JPA chapter, returning the entity makes the mapping visible and keeps the code small.

## H2 Configuration

The application uses an in-memory H2 database:

```yaml
{% include-markdown "../../code/17-jpa-basics/maven/src/main/resources/application.yml" comments=false %}
```

`jdbc:h2:mem:authors` creates a database in the application process. `ddl-auto: create-drop` asks Hibernate to create the schema at startup and drop it at shutdown. That is convenient for a first JPA example and wrong as a long-term migration strategy. Chapter 18 replaces this with Flyway migrations against Postgres.

`show-sql: true` and `hibernate.format_sql: true` make Hibernate log generated SQL. That is useful while learning because you can connect repository calls to actual database operations.

!!! note "H2 is a learning tool here"
    H2 keeps this chapter self-contained. It is not the database strategy for the guide. From chapter 18 onward, tests run against Postgres with Testcontainers.

The generated schema comes from annotations and Hibernate defaults. That is acceptable only because the database is disposable. In a real service, schema changes are application changes and should be reviewed, versioned, and applied deliberately. Flyway enters in the next chapter for exactly that reason.

## Repository Test

The repository test is a JPA slice:

```java
{% include-markdown "../../code/17-jpa-basics/maven/src/test/java/dev/springboot4docs/ch_17_jpa_basics/AuthorRepositoryTest.java" comments=false %}
```

`@DataJpaTest` loads the JPA pieces: entities, repositories, an entity manager, and the database infrastructure needed for repository tests. It does not load the web layer or your controller. By default, each test runs in a transaction that rolls back at the end, so test data does not leak into the next method.

`TestEntityManager` seeds the database through JPA rather than through the repository method being tested. That keeps the setup independent from the query under test. The three tests cover the email lookup, the case-insensitive name fragment lookup, and the JPQL prefix query.

## Controller Test

The controller test is a Spring MVC slice:

```java
{% include-markdown "../../code/17-jpa-basics/maven/src/test/java/dev/springboot4docs/ch_17_jpa_basics/AuthorControllerWebMvcTest.java" comments=false %}
```

`@WebMvcTest(AuthorController.class)` loads the selected controller and MVC infrastructure. It does not load the real service or repository. `@MockitoBean AuthorService` supplies the service dependency as a Mockito mock.

This layer verifies routing, status codes, JSON rendering, request-body binding, and the `Location` header on create. It should not prove that Hibernate can insert a row. That belongs in repository and integration tests.

## Integration Test

The final test starts the application on a random port and talks to it through HTTP:

```java
{% include-markdown "../../code/17-jpa-basics/maven/src/test/java/dev/springboot4docs/ch_17_jpa_basics/AuthorIntegrationTest.java" comments=false %}
```

`@SpringBootTest(webEnvironment = RANDOM_PORT)` loads the full application. `@AutoConfigureRestTestClient` provides a `RestTestClient` bound to that server. The test creates an author, reads it back, deletes it, and confirms the deleted resource returns `404`.

Together, the three test styles answer different questions:

- Repository slice: do the JPA mappings and queries work?
- MVC slice: does the controller speak the right HTTP contract?
- Full integration: do the layers work together against H2?

Run the tests from the chapter project:

```bash
./mvnw -q -B test
```

## Run It

Start the application:

```bash
./mvnw spring-boot:run
```

Create an author:

```bash
curl -i -X POST http://localhost:8080/authors \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Octavia Butler",
    "email": "octavia@example.com"
  }'
```

The response is `201 Created` with a `Location` header like `/authors/1`.

List authors:

```bash
curl -i http://localhost:8080/authors
```

Read one author:

```bash
curl -i http://localhost:8080/authors/1
```

Delete it:

```bash
curl -i -X DELETE http://localhost:8080/authors/1
```

The delete response is `204 No Content`. A later `GET /authors/1` returns `404 Not Found`.

This is the minimum JPA loop: entity, repository, transaction boundary, controller, and tests at the right layers. Chapter 18 keeps the same basic application shape but swaps the teaching database for Postgres, adds Testcontainers, and introduces Flyway migrations.
