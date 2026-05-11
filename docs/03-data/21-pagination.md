# Pagination & Sorting

Returning every matching row is one of the easiest ways to make a good API fail in production. A search box that returns 30 articles in development might match 1,000,000 rows after a year of real data. The database has to read them, the application has to allocate them, JSON serialization has to write them, and the client has to wait for a response it probably cannot display usefully.

Pagination turns that unbounded result into an explicit contract: give me this window of rows, in this order, with this maximum size. Sorting is part of the same contract. A page without a deterministic sort can shift under the client because databases are free to return equal rows in any physical order unless you ask for one.

Spring Data gives you three common return shapes:

`Page<T>` is for screens that need total metadata. It returns the content plus values such as `totalElements` and `totalPages`. To produce those totals, Spring Data runs a count query in addition to the content query.

`Slice<T>` is for "next page" or "load more" screens. It does not know the total number of matching rows. It only knows whether another slice exists, typically by asking for one row more than the requested size.

`List<T>` is just a raw list. Use it when the query itself defines the limit, or when the caller does not need Spring Data's page metadata.

Those choices should come from the user experience, not from habit. A back-office table with numbered page buttons usually wants `Page<T>`, because the screen needs to know how many pages exist. A mobile activity feed usually wants `Slice<T>` or a cursor-shaped response, because the screen only needs the next batch. A scheduled job that asks for "the latest 20 rows older than this cursor" can use `List<T>` because the query already carries the limit.

## The Entity

The sample uses a small `Article` entity with a generated id, title, body, and publication timestamp:

```java
{% include-markdown "../../code/21-pagination/maven/src/main/java/dev/springboot4docs/ch_21_pagination/Article.java" comments=false %}
```

The timestamp matters because most article feeds are sorted newest first. The id matters because production cursor pagination should have a unique tie-breaker after the timestamp. Two rows can share the same `publishedAt`, but two rows cannot share the same primary key.

## Repository Queries

The repository shows all three return styles, plus two projection styles:

```java
{% include-markdown "../../code/21-pagination/maven/src/main/java/dev/springboot4docs/ch_21_pagination/ArticleRepository.java" comments=false %}
```

`findByTitleContainingIgnoreCase` is a derived query. Passing a `Pageable` tells Spring Data to add limit, offset, and sort clauses. Because it returns `Page<Article>`, Spring Data also executes a count query so the result can expose `getTotalElements()` and `getTotalPages()`.

`findByPublishedAtBefore` returns `Slice<Article>`. That is useful for a feed where the UI only needs to know whether to show a "load more" button. A slice avoids the count query. On a large filtered table, skipping `count(*)` can be the difference between an endpoint that stays cheap and one that gets slower as data grows.

`findTop20ByPublishedAtLessThanOrderByPublishedAtDescIdDesc` demonstrates a keyset query. It does not accept `Pageable`; the method name fixes the page size at 20 and orders by `(publishedAt desc, id desc)`. In a real high-volume feed, you normally carry both values in the cursor and use a predicate like `published_at < :publishedAt or (published_at = :publishedAt and id < :id)`. This simplified chapter keeps the request cursor to an `Instant`, while still showing the stable ordering pattern.

The important repository design rule is that the sort must match the access pattern. If the API says "newest articles first", make that order explicit. If you later add a database index, the natural index is the same shape as the query: `published_at desc, id desc`. The Java method name is long, but it makes the constraint visible at the call site: this is not an arbitrary page, it is the next fixed-size window after a cursor.

## Pageable And Sort

You can create a `Pageable` yourself:

```java
PageRequest.of(0, 20, Sort.by("publishedAt").descending());
```

The first argument is zero-based. `PageRequest.of(0, 20, ...)` asks for the first page. `PageRequest.of(1, 20, ...)` asks for the second page.

Spring MVC can also bind `Pageable` directly from request parameters. The default parameter names are `page`, `size`, and `sort`. This URL:

```bash
/articles?page=0&size=10&sort=publishedAt,desc
```

maps to the same idea as `PageRequest.of(0, 10, Sort.by("publishedAt").descending())`. The Java API uses `Sort.by("publishedAt").descending()`. The URL syntax is `sort=publishedAt,desc`. For multiple sort fields, repeat the parameter:

```bash
/articles?page=0&size=20&sort=publishedAt,desc&sort=id,desc
```

That binding is handled by Spring Data's MVC integration through a `HandlerMethodArgumentResolver`. The controller method can declare `Pageable pageable`, and the resolver creates the object from the incoming request before your method runs. This keeps controllers focused on application behavior instead of parsing integers and sort tokens by hand.

Always cap client-controlled page sizes. This chapter configures Spring Data's web binding to reject oversized requests above 100:

```yaml
{% include-markdown "../../code/21-pagination/maven/src/main/resources/application.yml" comments=false %}
```

That cap is not a replacement for thoughtful endpoints, but it prevents accidental or hostile requests such as `size=100000`.

## Projections

Returning entities is fine for small internal examples, but many read endpoints only need a subset of columns. Projections let the repository return a smaller view.

An interface projection is the compact option:

```java
{% include-markdown "../../code/21-pagination/maven/src/main/java/dev/springboot4docs/ch_21_pagination/ArticleSummary.java" comments=false %}
```

The repository method returns `Page<ArticleSummary>`. Spring Data creates a proxy that exposes `getId()` and `getTitle()`. Interface projections are a common fit for straightforward list views because they are terse and can be attached to derived query methods.

A record or class projection is more explicit:

```java
{% include-markdown "../../code/21-pagination/maven/src/main/java/dev/springboot4docs/ch_21_pagination/ArticleTitleView.java" comments=false %}
```

The repository uses a JPQL constructor expression:

```java
@Query("""
		select new dev.springboot4docs.ch_21_pagination.ArticleTitleView(a.id, a.title, a.publishedAt)
		from Article a
		where a.publishedAt > :after
		""")
Page<ArticleTitleView> findTitleViewsByPublishedAtAfter(@Param("after") Instant after, Pageable pageable);
```

Use interface projections when the view is simple and the derived query reads well. Use record or class projections when you want full control over the selected fields, constructor, names, and test assertions. Records are especially pleasant for response-shaped data because they are immutable and compare by value.

Projection choice also affects how obvious the query is to the next reader. Interface projections are concise, but the selected columns are inferred from accessor names. A constructor expression is noisier, but it says exactly which columns are selected and exactly which Java type receives them. When an endpoint becomes important to performance, that explicitness is often worth the extra lines.

## The Controller

The controller has two endpoints:

```java
{% include-markdown "../../code/21-pagination/maven/src/main/java/dev/springboot4docs/ch_21_pagination/ArticleController.java" comments=false %}
```

`GET /articles` accepts `q`, `page`, `size`, and `sort`. Notice that the method does not parse the pagination parameters itself. Spring MVC's pageable argument resolver builds the `Pageable` before the controller is called.

Example:

```bash
curl "http://localhost:8080/articles?q=spring&page=0&size=10&sort=publishedAt,desc"
```

For a traditional results screen, returning `Page<Article>` is convenient because the response includes the current page, page size, and totals. That makes it easy to render "page 1 of 5" or disable a "next" button when the current page is the last one.

`GET /articles/cursor` is the keyset demo. The request provides an `after` timestamp and an optional `limit`. The controller caps the limit to 20 because the repository method is intentionally fixed-size:

```bash
curl "http://localhost:8080/articles/cursor?after=2026-01-01T01:00:00Z&limit=20"
```

Offset pagination asks the database to skip rows: page 1000 with size 20 means "walk past 20,000 rows, then return 20." Indexes help the ordering, but the database still has to account for the skipped range. Keyset pagination changes the question to "start after this last row and return the next 20." With an index that matches the cursor and order, the database can seek to the cursor position and continue from there.

Qualitatively, the offset plan gets more expensive as the page number grows because the skipped rows are still part of the work. The keyset plan is anchored by values from the previous result, so the database can use the ordered index like a bookmark. That is why cursor pagination is the usual pattern for very large feeds and exports. It trades random page access for stable forward movement.

The tradeoff is product behavior. Offset pagination can jump to page 73 because page numbers are part of the model. Keyset pagination is better for feeds, timelines, exports, and "load more" flows where the user moves forward from the last item they saw.

Cursor APIs also need stable client contracts. A real API usually encodes the cursor as an opaque string instead of exposing raw database fields. Internally that string may contain `publishedAt` and `id`; externally it is just `nextCursor`. Opaque cursors let you change the internal shape later without breaking clients.

## Tests

The repository test seeds 50 rows and verifies each repository shape:

```java
{% include-markdown "../../code/21-pagination/maven/src/test/java/dev/springboot4docs/ch_21_pagination/ArticleRepositoryTests.java" comments=false %}
```

The page test checks content size plus `totalElements` and `totalPages`. That proves the method is returning a `Page`, not just a limited list. The slice test checks `hasNext()` without asserting totals, because totals are deliberately not part of the `Slice` contract. The keyset test fetches 20 newest rows, takes the last row's timestamp as the next cursor, and then proves the next query continues with older rows. The projection test checks both the interface projection and the record projection.

The MVC test focuses on argument binding:

```java
{% include-markdown "../../code/21-pagination/maven/src/test/java/dev/springboot4docs/ch_21_pagination/ArticleControllerTests.java" comments=false %}
```

`@WebMvcTest` loads the MVC layer around `ArticleController`. `@MockitoBean` replaces the repository with a mock. The test calls `/articles?q=spring&page=2&size=15&sort=publishedAt,desc`, captures the `Pageable` passed to the repository, and asserts that the resolver set page number, size, and descending sort correctly.

Run the chapter tests from the Maven project:

```bash
./mvnw -q -B test
```

Chapter 22 builds on these repository ideas with specifications: composing dynamic filters without creating a new derived query method for every possible search form.
