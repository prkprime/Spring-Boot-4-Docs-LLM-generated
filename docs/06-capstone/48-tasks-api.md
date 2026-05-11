# Tasks API

The capstone is a small production-shaped backend. It is not large, but it connects the important pieces from the course: REST, database migrations, user-scoped data, JWT authorization, integration tests, Docker packaging, and Fly.io deployment configuration.

The application exposes a task list for the authenticated JWT subject. A token with `tasks.read` can list tasks. A token with `tasks.write` can create and complete tasks. Every query is scoped by the JWT subject, so Alice cannot read Bob's tasks.

## Dependencies

The Maven build brings together the course's main dependencies:

```xml
{% include-markdown "../../code/48-tasks-api/maven/pom.xml" comments=false %}
```

The main runtime dependencies are MVC, Data JPA, Flyway, Spring Security, OAuth2 resource server support, Actuator, PostgreSQL, and the PostgreSQL Flyway plugin. The test runtime adds the Spring Boot Testcontainers integration and the PostgreSQL container module.

## Configuration

The application configuration is production-shaped:

```yaml
{% include-markdown "../../code/48-tasks-api/maven/src/main/resources/application.yml" comments=false %}
```

`DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` are runtime settings. They are not baked into the image. `ddl-auto: validate` means Hibernate checks that the entity model matches the schema, but it does not create or mutate tables. Flyway owns schema changes.

The JWT resource server is configured with a JWK set URI:

```yaml
jwk-set-uri: ${JWT_JWK_SET_URI:http://localhost:9000/oauth2/jwks}
```

In production this points at your identity provider. The capstone tests replace the decoder with a local test bean so the test suite can run offline.

## Migration

The database schema is explicit:

```sql
{% include-markdown "../../code/48-tasks-api/maven/src/main/resources/db/migration/V1__create_tasks.sql" comments=false %}
```

The table stores the owner subject, title, details, status, creation time, and completion time. The `(owner_subject, status)` index supports the common "show my open tasks" shape. Even though the sample lists all tasks, the index is the kind of small production detail you should think about while designing the table.

## Domain Model

The status enum is intentionally tiny:

```java
{% include-markdown "../../code/48-tasks-api/maven/src/main/java/dev/springboot4docs/ch_48_tasks_api/TaskStatus.java" comments=false %}
```

The entity maps to the Flyway-created table:

```java
{% include-markdown "../../code/48-tasks-api/maven/src/main/java/dev/springboot4docs/ch_48_tasks_api/TaskItem.java" comments=false %}
```

Notice that `ownerSubject` is persisted with every task. That is the security boundary at the data layer. The controller authenticates the caller, but the repository query still scopes by owner. Do not fetch a row by id and then remember to check the owner later; make the owner part of the query.

The repository exposes owner-scoped methods:

```java
{% include-markdown "../../code/48-tasks-api/maven/src/main/java/dev/springboot4docs/ch_48_tasks_api/TaskRepository.java" comments=false %}
```

The service keeps transaction boundaries out of the controller:

```java
{% include-markdown "../../code/48-tasks-api/maven/src/main/java/dev/springboot4docs/ch_48_tasks_api/TaskService.java" comments=false %}
```

The service has three operations: list, create, and complete. The `complete` method uses `findByIdAndOwnerSubject`, so completing another user's task returns the same not-found path as completing a task that does not exist.

## REST API

The controller owns HTTP details and DTOs:

```java
{% include-markdown "../../code/48-tasks-api/maven/src/main/java/dev/springboot4docs/ch_48_tasks_api/TaskController.java" comments=false %}
```

`JwtAuthenticationToken` gives the controller access to the decoded JWT. The controller uses the token subject as the owner. In a larger app you might wrap that in a `CurrentUser` abstraction, but the capstone keeps the subject visible so the data boundary is easy to see.

`POST /api/tasks` returns `201 Created` with a `Location` header. `PATCH /api/tasks/{id}/complete` changes status from `OPEN` to `DONE`. `GET /api/tasks` lists only the current subject's tasks.

Errors use `ProblemDetail`, continuing the pattern from the web chapters. A missing or cross-user task returns a `404` problem with a clear title and detail.

## Security

The security configuration is stateless:

```java
{% include-markdown "../../code/48-tasks-api/maven/src/main/java/dev/springboot4docs/ch_48_tasks_api/SecurityConfig.java" comments=false %}
```

Health and info endpoints are public. `GET /api/tasks/**` requires `SCOPE_tasks.read`. Mutating task endpoints require `SCOPE_tasks.write`. That means a read-only token can inspect tasks but cannot create or complete them.

The application does not create JWTs. It validates bearer tokens from an issuer. That is the production pattern from the security chapters: the API is a resource server, not an authorization server.

## Tests

The integration tests start the whole application with a real PostgreSQL container:

```java
{% include-markdown "../../code/48-tasks-api/maven/src/test/java/dev/springboot4docs/ch_48_tasks_api/TestcontainersConfiguration.java" comments=false %}
```

The test suite replaces JWT decoding with an in-memory decoder:

```java
{% include-markdown "../../code/48-tasks-api/maven/src/test/java/dev/springboot4docs/ch_48_tasks_api/TasksApiIntegrationTest.java" comments=false %}
```

The tests prove four production behaviors:

- Anonymous requests are rejected.
- `tasks.read` can list tasks but cannot create them.
- `tasks.write` can create and complete a task.
- A different JWT subject cannot see another user's task.

Run the chapter:

```bash
./mvnw -q -B test
```

For local manual testing with disposable Postgres:

```java
{% include-markdown "../../code/48-tasks-api/maven/src/test/java/dev/springboot4docs/ch_48_tasks_api/TestApplication.java" comments=false %}
```

Start it with:

```bash
./mvnw spring-boot:test-run
```

The test runner gives you Postgres, but it does not give you a real JWT issuer. For manual API calls, either run a real issuer and set `JWT_JWK_SET_URI`, or temporarily add a local development decoder. Do not ship a fake decoder in production code.

## Docker

The image uses the same multi-stage pattern from Part V:

```dockerfile
{% include-markdown "../../code/48-tasks-api/maven/Dockerfile" comments=false %}
```

The `.dockerignore` file keeps local build output out of the build context:

```text
{% include-markdown "../../code/48-tasks-api/maven/.dockerignore" comments=false %}
```

Build it locally:

```bash
docker build -t sb4-docs/48-tasks-api:dev .
```

The image still needs runtime configuration: database connection settings and a JWT JWK set URI. Those belong in environment variables or platform secrets.

## Fly.io

The deployment file is ready for Fly.io:

```toml
{% include-markdown "../../code/48-tasks-api/maven/fly.toml" comments=false %}
```

Set secrets before deploying:

```bash
fly secrets set DATABASE_URL='jdbc:postgresql://.../tasks'
fly secrets set DATABASE_USERNAME='tasks'
fly secrets set DATABASE_PASSWORD='change-me'
fly secrets set JWT_JWK_SET_URI='https://issuer.example.com/oauth2/jwks'
```

Then deploy:

```bash
fly deploy
```

The readiness check points at `/actuator/health/readiness`, so Fly can avoid routing traffic to a machine that has not finished starting.

## What To Carry Forward

This capstone is small enough to understand in one sitting, but its boundaries are real:

- Schema changes are migrations.
- Security is JWT validation, not local token minting.
- Data access is scoped by owner in repository queries.
- Tests use the same database family as production.
- The image is configured at runtime.
- Deployment health checks call Actuator, not a random endpoint.

That is the production baseline for a backend API. Larger apps add more modules, more workflows, and more operational policy, but the spine stays the same.
