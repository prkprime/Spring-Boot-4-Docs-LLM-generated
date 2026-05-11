# Auditing & Soft Deletes

Most business rows need more history than their visible fields show. A note has a body, but support, compliance, and debugging often need to know when it was created, when it was last changed, and which actor made those changes. Audit columns are a small habit that pays off later: they give you a forensic trail for incident review, make compliance reporting less guessy, and turn many "why did this change?" tickets into a direct database lookup.

Spring Data JPA can fill those columns for you. You mark fields with `@CreatedDate`, `@LastModifiedDate`, `@CreatedBy`, and `@LastModifiedBy`, register the auditing entity listener, and enable auditing in configuration. Hibernate can also help with soft deletes. Instead of physically deleting a row, it marks the row as deleted and hides it from normal entity queries.

This chapter uses both pieces together: audit columns for who and when, plus Hibernate's modern `@SoftDelete` annotation for rows that should disappear from the application without immediately leaving the table.

## Shared Audit Columns

The sample keeps audit fields in a mapped superclass:

```java
{% include-markdown "../../code/23-auditing/maven/src/main/java/dev/springboot4docs/ch_23_auditing/Auditable.java" comments=false %}
```

`@MappedSuperclass` means the fields are inherited by each entity table. There is no separate `auditable` table and no join. In this example, the `note` table gets `created_at`, `updated_at`, `created_by`, and `updated_by` columns directly.

`@EntityListeners(AuditingEntityListener.class)` is the hook that lets Spring Data JPA inspect entity lifecycle events. On insert, it fills `@CreatedDate` and `@CreatedBy`. On insert and update, it fills `@LastModifiedDate` and `@LastModifiedBy`.

An `@Embeddable` audit object is another reasonable design. It groups the fields into a value object and can make entity classes visually smaller. For this follow-along project, `@MappedSuperclass` is the simpler choice because the audit fields behave like ordinary inherited columns and do not need extra `@Embedded` declarations in every entity.

## Enabling Auditing

Auditing is opt-in:

```java
{% include-markdown "../../code/23-auditing/maven/src/main/java/dev/springboot4docs/ch_23_auditing/AuditConfig.java" comments=false %}
```

`@EnableJpaAuditing(auditorAwareRef = "auditorAware")` turns on the listener support and tells Spring Data which bean supplies the current actor. Date fields do not need a custom provider in this chapter; Spring Data can set `Instant` values from its default time source.

`AuditorAware<String>` answers one question: who is making the current change? The demo first looks for an `X-User` request header, then for a request-scoped `currentUser` attribute, and finally falls back to `"system"`. A real application usually reads from Spring Security's `SecurityContextHolder` instead:

```java
SecurityContextHolder.getContext().getAuthentication().getName()
```

The important part is the boundary. Entities do not know about HTTP, sessions, JWTs, or authentication. They only expose audit fields. The auditing configuration adapts the current request or security context into the simple value stored in the database.

## The Note Entity

The entity itself stays small:

```java
{% include-markdown "../../code/23-auditing/maven/src/main/java/dev/springboot4docs/ch_23_auditing/Note.java" comments=false %}
```

`Note` extends `Auditable`, so it receives all four audit columns. Its own persistent fields are just `id` and `body`.

`@SoftDelete` is the Hibernate 6.4+ feature that replaces the older pair of custom annotations for common soft-delete cases. With the default strategy, Hibernate adds a boolean `deleted` column. Inserts write `deleted = false`. Deletes become updates that set `deleted = true`. Normal selects include a predicate that hides deleted rows, and entity updates are constrained so deleted rows are not accidentally modified through the normal entity path.

That is the key difference from a manual `deleted` field. If you only add `private boolean deleted`, every repository query, specification, and custom JPQL statement has to remember `where deleted = false`. `@SoftDelete` makes that default part of the mapping.

The generated SQL is worth noticing. A normal insert includes the soft-delete column with the active value. A repository delete becomes an `update note set deleted = true where id = ? and deleted = false`. A normal entity select adds `where deleted = false`. That last predicate is why `findAll()` and `findById(id)` behave as if the row is gone after deletion even though the physical table still contains it.

Before Hibernate 6.4, a common mapping looked like this:

```java
@SQLDelete(sql = "update note set deleted = true where id = ?")
@Where(clause = "deleted = false")
```

That approach still appears in many existing projects. It is more verbose, and you own more of the SQL details yourself. In newer Hibernate versions, prefer `@SoftDelete` when the default boolean-column model fits.

## Repository

The repository mostly uses standard Spring Data JPA:

```java
{% include-markdown "../../code/23-auditing/maven/src/main/java/dev/springboot4docs/ch_23_auditing/NoteRepository.java" comments=false %}
```

`JpaRepository<Note, Long>` gives us `save`, `findAll`, `findById`, `delete`, and `deleteById`. Because `Note` is soft-deletable, those methods operate through Hibernate's mapping. A delete call does not remove the row from the table; it marks the row deleted.

The native query is deliberately unusual. It asks the database for the raw `deleted` column by id. Native SQL does not go through Hibernate's entity query filter, so it can see the row after a soft delete. That makes it useful in tests and in admin or retention jobs, but it is also a warning: native queries can bypass the safety net.

That warning applies to any SQL path outside the entity manager's normal mapped operations. A batch job, analytics export, database view, or hand-written report query must decide whether it wants active rows only or all rows including deleted ones. Soft delete is a mapping rule, not a universal database law.

## Controller

The web layer exposes only visible notes:

```java
{% include-markdown "../../code/23-auditing/maven/src/main/java/dev/springboot4docs/ch_23_auditing/NoteController.java" comments=false %}
```

`POST /notes` creates a note from the request body. The controller does not set `createdAt`, `updatedAt`, `createdBy`, or `updatedBy`. Those fields are persistence concerns, and Spring Data fills them when the entity is saved.

`GET /notes` calls `findAll()`. Because the entity has `@SoftDelete`, this endpoint returns only rows where `deleted = false`. There is no controller-level soft-delete condition.

`DELETE /notes/{id}` checks that the note exists and then calls `deleteById`. Hibernate turns that into an update of the soft-delete indicator. From the controller's point of view, this still behaves like a normal REST delete: the note is gone from the visible collection and repeated reads by id do not find it.

The response DTO includes the audit fields so you can see the automatic values in the HTTP response after a create. In a production API you might expose all of them, some of them, or none of them depending on the audience. The persistence mapping is independent from that API decision.

## Tests

The repository tests prove the behavior that matters most:

```java
{% include-markdown "../../code/23-auditing/maven/src/test/java/dev/springboot4docs/ch_23_auditing/NoteRepositoryTests.java" comments=false %}
```

The test uses the Spring Boot 4 `@DataJpaTest` import from `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`. `@Import(AuditConfig.class)` brings the auditing configuration into the JPA slice. Without that import, the entity listener would be present but auditing would not be enabled for the test context.

`insertPopulatesAuditColumns` saves a note and flushes it. The assertions check both sides of auditing: timestamp fields are non-null, and actor fields use the fallback `"system"` user because the test is not running inside an HTTP request.

`updateChangesUpdatedAtButNotCreatedAt` captures the original creation time and first update time, changes the body, and flushes again. `createdAt` must remain stable. `updatedAt` must move forward. That distinction matters because many systems use creation time for ordering, retention windows, or reporting; updating a row should not rewrite its birth date.

`deleteMarksTheRowButRepositoryQueriesHideIt` is the soft-delete proof. It saves a row, deletes it, flushes the persistence context, and clears the `EntityManager` so the next assertions hit the database instead of returning a managed object from memory. `findAll()` is empty. `findById(id)` is empty. The custom native query still sees the same physical row and confirms that `deleted` is `true`.

Clearing the persistence context is a small but important testing detail. Without it, a test can accidentally assert against an already-managed entity instance and miss the SQL behavior it meant to verify. When a test is about database visibility, flush first, clear second, then query again.

Run the chapter tests from the Maven project:

```bash
./mvnw -q -B test
```

## Run It

Start the application from the chapter directory:

```bash
./mvnw spring-boot:run
```

Create a note. The `X-User` header is optional, but it demonstrates `AuditorAware`:

```bash
curl -i -X POST http://localhost:8080/notes \
  -H "Content-Type: application/json" \
  -H "X-User: ada" \
  -d '{"body":"Remember the audit trail"}'
```

List visible notes:

```bash
curl http://localhost:8080/notes
```

Delete one:

```bash
curl -i -X DELETE http://localhost:8080/notes/1
```

After the delete, `GET /notes` no longer returns that row. In the database, though, the row still exists with `deleted = true`.

## Tradeoffs

Soft delete is useful when deletion means "remove from normal workflows" rather than "destroy the data." It preserves audit history, makes undo possible, and can reduce accidental damage from foreign-key cascades. If an invoice line, comment, or note is part of a larger business trail, a soft delete can be the right default.

It is not free. Every table keeps more rows, indexes include dead application data, and reporting jobs can accidentally count deleted records if they use native SQL or ETL exports that ignore the application mapping. Large tables may need partial indexes, retention jobs, or archival tables so soft-deleted data does not become permanent clutter.

There is also a legal and product boundary. Do not soft-delete data when the user has invoked a privacy-law deletion right and your system is required to remove or anonymize it. In that case, actually delete the personal data, anonymize it irreversibly, or move it through the retention process your legal and security teams have approved.

Use audit columns broadly. Use soft delete deliberately.

Chapter 24 moves from preserving rows to speeding up reads with Caffeine caching.
