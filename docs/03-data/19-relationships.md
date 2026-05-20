# Relationships & N+1

Relationships are where JPA starts to feel useful and dangerous at the same time. Mapping an `Author` to many `Book` rows is straightforward. Accidentally turning one repository call into dozens of SQL statements is also straightforward.

This chapter uses H2 again so the relationship tests stay small and fast. Chapter 18 showed the Postgres and Testcontainers setup; we do not need to repeat that ceremony every time we want to teach one persistence behavior. The important thing here is not the database engine. The important thing is proving how many SQL statements Hibernate prepares for each repository method.

The sample application has two entities:

```text
Author 1 ---- * Book
```

An author has a lazy collection of books. A book has a lazy many-to-one reference back to its author. The application also exposes a few read endpoints so you can start the app, hit the naive and fixed queries, and watch the SQL logs.

## The Relationship Annotations

JPA has four core relationship annotations:

```java
@OneToOne
@OneToMany
@ManyToOne
@ManyToMany
```

The names describe the cardinality between two entity types. A `Book` has one `Author`, so `Book.author` is `@ManyToOne`: many books can point at one author. An `Author` has many `Book` rows, so `Author.books` is `@OneToMany`.

One side owns the relationship. The owning side is the side that writes the foreign key. In this chapter, the `book` table has an `author_id` column, so `Book.author` is the owning side:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "author_id", nullable = false)
private Author author;
```

The inverse side points back to the owning side with `mappedBy`:

```java
@OneToMany(mappedBy = "author", cascade = CascadeType.ALL, orphanRemoval = true)
private List<Book> books = new ArrayList<>();
```

`mappedBy = "author"` means: do not create another join column or join table for this collection; the relationship is already mapped by the `author` field on `Book`.

The helper method on `Author` matters too:

```java
public void addBook(Book book) {
	this.books.add(book);
	book.setAuthor(this);
}
```

Bidirectional relationships are two Java references pointing at one database relationship. JPA does not automatically keep both object references synchronized for you while your code is building the graph. If you only add a book to `author.books` but never set `book.author`, the owning side is still missing the foreign key. If you only set `book.author` but never add it to the collection, the database insert can still work, but the in-memory `Author` object is stale. A small helper method keeps the aggregate consistent. `Book` exposes that setter as package-private with Lombok so callers outside the aggregate do not casually rewire the relationship.

The complete entities are small:

```java
{% include-markdown "../../code/19-relationships/maven/src/main/java/dev/springboot4docs/ch_19_relationships/Author.java" comments=false %}
```

```java
{% include-markdown "../../code/19-relationships/maven/src/main/java/dev/springboot4docs/ch_19_relationships/Book.java" comments=false %}
```

There is one choice here that should become muscle memory: make relationships lazy unless you have a specific reason not to.

JPA defaults are awkward. `@OneToMany` and `@ManyToMany` are lazy by default, but `@ManyToOne` and `@OneToOne` are eager by default. That eager default is a footgun. It makes a simple `Book` query pull author data even when the caller does not need it, and it becomes painful once the model grows. This chapter explicitly sets `Book.author` to `LAZY`. In real applications, do that for almost every relationship, then fetch exactly what each use case needs.

That rule also keeps repository methods honest. A method named `findAll()` should not secretly drag half the object model into memory because a relationship somewhere was left eager. Keep the default load small. Add bigger read shapes intentionally.

## The N+1 Shape

N+1 means one query for the parent rows, followed by one query per parent when a lazy association is accessed.

For three authors, the naive flow is:

```text
1 query  -> select all authors
3 queries -> select books for author 1, author 2, author 3
```

That is four queries for three authors. With 100 authors, it becomes 101 queries. The code often looks harmless:

```java
var authors = authorRepository.findAll();

for (Author author : authors) {
	author.getBooks().size();
}
```

The first line loads authors. The loop touches the lazy `books` collection for each author. Hibernate has no books loaded yet, so each collection access needs another SQL statement.

With SQL logging enabled, the shape looks like this:

```sql
select a1_0.id,a1_0.email,a1_0.name
from author a1_0

select b1_0.author_id,b1_0.id,b1_0.publication_year,b1_0.title
from book b1_0
where b1_0.author_id=?

select b1_0.author_id,b1_0.id,b1_0.publication_year,b1_0.title
from book b1_0
where b1_0.author_id=?

select b1_0.author_id,b1_0.id,b1_0.publication_year,b1_0.title
from book b1_0
where b1_0.author_id=?
```

This is the most common JPA performance bug because it hides behind normal object navigation. The database work happens later, when code reads a property.

## Repository Fixes

The repository has the naive method inherited from `JpaRepository`, plus three intentional alternatives:

```java
{% include-markdown "../../code/19-relationships/maven/src/main/java/dev/springboot4docs/ch_19_relationships/AuthorRepository.java" comments=false %}
```

`findAll()` is not wrong by itself. It is wrong for a use case that also needs every author's books. Lazy loading lets one repository method serve simple cases cheaply, but the caller must choose a different query when it needs the association.

The first fix is `@EntityGraph`:

```java
@EntityGraph(attributePaths = "books")
@Query("select a from Author a")
List<Author> findAllWithBooks();
```

An entity graph is declarative. It says: run this query for authors, but fetch the `books` association as part of the same load plan. The method still returns managed `Author` entities. Hibernate can dirty-check them, and the persistence context tracks them like any other entity result.

The SQL becomes one join:

```sql
select a1_0.id,b1_0.author_id,b1_0.id,b1_0.publication_year,b1_0.title,a1_0.email,a1_0.name
from author a1_0
left join book b1_0 on a1_0.id=b1_0.author_id
```

The second fix is an explicit JPQL fetch join:

```java
@Query("select a from Author a left join fetch a.books")
List<Author> findAllWithBooksFetchJoin();
```

This produces the same basic plan, but the fetch is part of the JPQL itself. Use this when the query text already matters: filters, joins, ordering, or a shape that is easier to understand directly in JPQL. Use `@EntityGraph` when you want to keep a simpler query and attach fetch behavior at the repository method.

The third fix is not to return entities at all. If the screen only needs author names and book counts, do not load authors and books:

```java
{% include-markdown "../../code/19-relationships/maven/src/main/java/dev/springboot4docs/ch_19_relationships/AuthorWithBookCount.java" comments=false %}
```

The repository query constructs the record directly:

```java
@Query("""
		select new dev.springboot4docs.ch_19_relationships.AuthorWithBookCount(a.name, count(b))
		from Author a
		left join a.books b
		group by a.id
		""")
List<AuthorWithBookCount> findAuthorBookCounts();
```

Projection queries are often the best read-model choice. They fetch only the columns you asked for, skip entity materialization for the child collection, and avoid dirty-checking overhead. The trade-off is that projections are not managed entities. You cannot modify the projection and expect Hibernate to flush changes. That is usually a feature for read endpoints.

The choice between these three fixes is practical:

- Use `@EntityGraph` when the parent query is simple and the repository method should declare the fetch plan without making the JPQL more prominent than the use case.
- Use `left join fetch` when you are already writing JPQL and want the query text to show the complete database shape.
- Use a projection when the caller needs a read model, not a mutable entity graph.

Returning entities is useful when the next step is domain behavior inside the same transaction. Returning projections is useful when the next step is JSON, a table row, a report, or a view model. Most web read endpoints should lean toward projections or response DTOs because they make the boundary explicit.

## Cartesian Product Caveat

Fetch joins are powerful, but they are not a universal "make it fast" switch. Fetch joining one collection is usually fine. Fetch joining two collections can explode the row count.

Imagine `Author` has 2 books and 5 awards. A query that fetch joins both collections can produce 10 rows for that one author before Hibernate reassembles the object graph. Across a page of authors, the result set can become much larger than expected.

When you need multiple collections, prefer one explicit fetch for the collection needed immediately, keep the others lazy, and consider Hibernate batch fetching for the rest. Batch fetching does not make the problem one query, but it can turn many tiny selects into a small number of `where id in (...)` selects. Hibernate offers `@BatchSize` for this:

```java
@BatchSize(size = 25)
@OneToMany(mappedBy = "author")
private List<Book> books = new ArrayList<>();
```

Use it as a fallback when you cannot refactor the query shape cleanly, or when several code paths lazily touch the same association and you want fewer round-trips.

Do not use batch fetching to excuse unclear read paths. If a screen always needs authors and books together, write the query that says so. Batch fetching is better for secondary associations that are sometimes touched and sometimes ignored.

## Proving It In Tests

Do not guess about N+1. Test it.

This chapter uses a JPA slice and Hibernate `Statistics`. The test enables statistics, seeds three authors with two books each, clears the persistence context, clears the statistics counters, runs one repository method, and then asserts the prepared statement count.

```java
{% include-markdown "../../code/19-relationships/maven/src/test/java/dev/springboot4docs/ch_19_relationships/RelationshipsTest.java" comments=false %}
```

The important setup is this:

```java
var stats = entityManager.getEntityManagerFactory()
		.unwrap(SessionFactory.class)
		.getStatistics();
stats.clear();
```

After that, `stats.getPrepareStatementCount()` tells the test how many SQL statements Hibernate prepared for the behavior under test.

The naive test expects four statements: one for authors, then one per author when the loop accesses `getBooks()`:

```java
var result = authors.findAll();

result.forEach((author) -> assertThat(author.getBooks()).hasSize(2));

assertThat(stats.getPrepareStatementCount()).isEqualTo(4);
```

The entity graph and fetch join tests both assert one statement. The projection test also asserts one statement and verifies the count result. This is the test pattern to keep. When a repository method is supposed to be a single-query read path, prove it with statistics instead of hoping the generated SQL stays reasonable.

The `entityManager.clear()` call in the setup is part of the proof. The test seeds data in the same transaction as the assertion. Without clearing the persistence context, Hibernate might satisfy later reads from objects it already has in memory. That would make the statement count look better than a real request. Flush the seed data, clear the persistence context, clear the statistics, and only then run the repository method under test.

This style is more durable than asserting exact log text. SQL aliases change across Hibernate versions, and harmless formatting differences should not break a persistence test. Statement counts are the behavior you care about for N+1. Logs are still useful while learning and debugging, but statistics give the test a stable signal.

The application configuration also keeps two important switches visible:

```yaml
{% include-markdown "../../code/19-relationships/maven/src/main/resources/application.yml" comments=false %}
```

`spring.jpa.properties.hibernate.generate_statistics: true` enables the counters used by the tests. `spring.jpa.open-in-view: false` disables Open Session in View.

Open Session in View keeps the persistence context open while the web view is rendered. That can hide lazy loading bugs because the controller or JSON serialization can still trigger SQL after the service method returns. Keep it off. Fetch the data needed by a request inside the transactional boundary.

That also explains the common `LazyInitializationException`: code touched a lazy association after the session was closed. The fix is not to turn everything eager. Fetch the association in the query for that use case, return a projection, or put the read operation behind an appropriate `@Transactional(readOnly = true)` boundary.

The sample API uses a read service for exactly that reason:

```java
{% include-markdown "../../code/19-relationships/maven/src/main/java/dev/springboot4docs/ch_19_relationships/AuthorReadService.java" comments=false %}
```

The service method runs inside a read-only transaction, maps entities to response records, and returns plain data to the controller. The naive method still demonstrates N+1 because it touches the lazy collection inside the transaction. The fixed methods demonstrate better query shapes inside the same boundary.

## Try The API

The application has a tiny read API:

```java
{% include-markdown "../../code/19-relationships/maven/src/main/java/dev/springboot4docs/ch_19_relationships/AuthorController.java" comments=false %}
```

Start the app:

=== "Maven"
    ```bash
    ./mvnw spring-boot:run
    ```

=== "Gradle"
    ```bash
    ./gradlew bootRun
    ```

Then compare the SQL logs for these endpoints:

```bash
curl http://localhost:8080/authors/naive
curl http://localhost:8080/authors/entity-graph
curl http://localhost:8080/authors/fetch-join
curl http://localhost:8080/authors/book-counts
```

The naive endpoint loads authors first and touches `books` later while mapping the response. The entity graph and fetch join endpoints load the books with the authors. The book-count endpoint does not load `Book` entities at all.

The seed data for local runs is deliberately tiny:

```sql
{% include-markdown "../../code/19-relationships/maven/src/main/resources/data.sql" comments=false %}
```

Small seed data is enough because the query pattern is what matters. Three authors already prove 1+N. More rows would only make the log noisier.

Run the tests from the chapter project:

=== "Maven"
    ```bash
    ./mvnw test
    ```

=== "Gradle"
    ```bash
    ./gradlew test
    ```

Chapter 20 moves from query shape to transaction boundaries: where transactions begin, what `readOnly` really means, when changes flush, and how rollback behavior affects service design.
