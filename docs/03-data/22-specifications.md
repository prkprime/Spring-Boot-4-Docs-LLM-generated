# Specifications

Dynamic search screens are where tidy repository methods start to break down. A product catalog might let the user type part of a name, choose a category, enter a minimum price, enter a maximum price, and decide whether to show only products that are in stock. Every field is optional. The user might send one filter, all filters, or none of them.

You can solve that by building JPQL strings with `if` statements, but it gets miserable quickly. You have to track whether the `where` clause has already started, keep parameter names in sync, and make sure every new filter composes correctly with the old ones. Derived query methods do not scale either. A method named `findByNameContainingIgnoreCaseAndCategoryIgnoreCaseAndPriceCentsBetweenAndInStockTrue` works for one combination, but a real search form has many combinations.

Spring Data JPA's answer is `JpaSpecificationExecutor` plus `Specification<T>`. A specification is a small, composable query predicate. Instead of writing one repository method for every filter combination, you write one specification per filter and combine the ones that apply to the current request.

## The Entity

The sample catalog uses a `Product` entity with a few searchable fields:

```java
{% include-markdown "../../code/22-specifications/maven/src/main/java/dev/springboot4docs/ch_22_specifications/Product.java" comments=false %}
```

The fields are deliberately ordinary. `name` and `category` are strings, `priceCents` is an integer so the example avoids floating point money, `inStock` is a boolean, and `createdAt` gives us a stable field to sort by. There is no query logic in the entity. The query behavior belongs in the repository and specification helpers.

## The Repository

A repository opts into specification queries by extending `JpaSpecificationExecutor<T>`:

```java
{% include-markdown "../../code/22-specifications/maven/src/main/java/dev/springboot4docs/ch_22_specifications/ProductRepository.java" comments=false %}
```

`JpaRepository` still gives us the usual CRUD methods. `JpaSpecificationExecutor<Product>` adds methods such as:

```java
List<Product> findAll(Specification<Product> spec);
Page<Product> findAll(Specification<Product> spec, Pageable pageable);
List<Product> findAll(Specification<Product> spec, Sort sort);
```

The second overload is the one most search endpoints want. It applies the dynamic predicate and still uses Spring Data's normal pagination and sorting support. That means a request can filter by category and price while still sending `page`, `size`, and `sort` parameters.

## What A Specification Is

A `Specification<T>` is a functional interface. In practice, you usually write it as a lambda:

```java
(root, query, builder) -> builder.equal(root.get("category"), "shoes")
```

The three arguments are the JPA Criteria API pieces. `root` represents the entity table being queried. `query` represents the larger Criteria query. `builder` creates predicates such as `equal`, `like`, `between`, `isTrue`, `and`, and `or`.

The useful Spring Data convention is that returning `null` from a specification means "no constraint." That is perfect for optional filters. A blank search term does not need a special controller branch. The specification can simply return `null`, and Spring Data will ignore it when composing the final predicate.

Composition is the main reason to use specifications:

```java
Specification<Product> spec = Specification
	.where(ProductSpecifications.category("shoes"))
	.and(ProductSpecifications.priceBetween(5000, 15000))
	.and(ProductSpecifications.inStock());
```

Spring Data also has `Specification.allOf(...)`, which is convenient when you are collecting optional filters:

```java
Specification<Product> spec = Specification.allOf(
		ProductSpecifications.nameContains(q),
		ProductSpecifications.category(category),
		ProductSpecifications.priceBetween(min, max));
```

For an either-or search, use `or`:

```java
Specification<Product> spec = ProductSpecifications.category("shoes")
	.or(ProductSpecifications.category("bags"));
```

The end result is still one database query. You are composing Java objects that describe predicates; you are not loading all products and filtering them in memory.

That distinction matters. A specification is not a post-processing callback. Spring Data hands the composed specification to the JPA provider, and Hibernate turns the Criteria predicates into SQL. The database still does the filtering, ordering, limiting, and counting. Your Java code only decides which predicates belong in this request.

One practical habit is to keep specifications small. A method named `availableShoesUnder150Dollars()` might be useful once, but it hides three independent decisions. `category("shoes")`, `priceBetween(0, 15000)`, and `inStock()` are more reusable, easier to test, and easier to rearrange when a new screen needs a slightly different combination.

## Product Specifications

This chapter keeps the reusable predicates in a small utility class:

```java
{% include-markdown "../../code/22-specifications/maven/src/main/java/dev/springboot4docs/ch_22_specifications/ProductSpecifications.java" comments=false %}
```

`nameContains` does a case-insensitive `like` query. It lowers both sides of the comparison and wraps the search term in `%` so a request for `run` can match `Trail Running Shoes` and `Merino Running Socks`. If the incoming term is `null` or blank, it returns `null`, so that filter disappears from the composed query.

`category` follows the same optional-filter pattern but uses equality instead of `like`. The example normalizes the comparison to lowercase so `SHOES`, `Shoes`, and `shoes` behave the same way.

`priceBetween` maps directly to a Criteria `between` predicate. The controller decides when that range should be included. If the client sends only a minimum price, the controller uses `Integer.MAX_VALUE` as the upper bound. If the client sends only a maximum price, the controller uses `0` as the lower bound.

`inStock` is the smallest predicate: `builder.isTrue(root.get("inStock"))`. We keep it as a method anyway because it reads well when composed with the other filters. The call site says what the business filter means; the helper hides the Criteria API detail.

## The Controller

The controller accepts the search form as ordinary query parameters:

```java
{% include-markdown "../../code/22-specifications/maven/src/main/java/dev/springboot4docs/ch_22_specifications/ProductSearchController.java" comments=false %}
```

`Optional<String>` and `Optional<Integer>` make the request shape visible: every filter is allowed to be absent. The controller maps each present value to a specification and passes `null` for missing values. `Specification.allOf(...)` combines the non-null predicates into one final specification.

The boolean filter is handled slightly differently:

```java
inStock.filter(Boolean::booleanValue)
		.map((ignored) -> ProductSpecifications.inStock())
		.orElse(null)
```

That means `?inStock=true` adds the stock predicate. Omitting `inStock`, or sending `inStock=false`, leaves stock out of the query. This is a common shape for a checkbox labeled "in stock only." If your product behavior needs `false` to mean "show only out of stock items," you would add a second specification such as `outOfStock()`.

The controller does not parse `page`, `size`, or `sort`. Spring MVC and Spring Data bind those into the `Pageable` argument. This request:

```bash
/products?q=run&category=shoes&minPrice=5000&maxPrice=15000&inStock=true&page=0&size=10&sort=priceCents,desc
```

calls the repository with one composed specification and one `Pageable`. The repository returns `Page<Product>`, so the response carries the matching rows plus page metadata such as total elements and total pages.

The application also enables Spring Data's DTO page serialization mode:

```java
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
```

That keeps the JSON page shape stable. Without it, Spring Data warns that serializing `PageImpl` directly is not a public wire contract.

This keeps the endpoint honest about responsibility. The controller translates HTTP parameters into repository inputs. The specifications describe database predicates. The repository executes the query. If a new optional filter appears later, you add one small specification and one line in the `allOf(...)` call instead of creating another repository method or editing a fragile query string builder.

## Tests

The JPA test seeds 20 products and exercises several filter combinations:

```java
{% include-markdown "../../code/22-specifications/maven/src/test/java/dev/springboot4docs/ch_22_specifications/ProductRepositoryTests.java" comments=false %}
```

The first test combines name and category. It proves that the case-insensitive `like` predicate narrows the search to the one shoe product whose name contains `running`.

The second test combines category, price range, and stock status. That is the kind of combination that becomes awkward with derived query methods because each optional field multiplies the number of possible method names.

The third test intentionally passes blank and `null` values into two specifications. Those methods return `null`, and the composed query still applies the price and stock filters. This is the optional-filter behavior that keeps controller code small.

The fourth test verifies that pagination and sorting still apply to specification queries. It asks for the first two in-stock gear products sorted by `createdAt` descending and asserts both the page content and the total metadata.

The MVC test focuses on parameter binding:

```java
{% include-markdown "../../code/22-specifications/maven/src/test/java/dev/springboot4docs/ch_22_specifications/ProductSearchControllerTests.java" comments=false %}
```

`@WebMvcTest` loads the controller layer. `@MockitoBean` replaces the repository with a Mockito mock. The test sends all filter parameters plus `page`, `size`, and `sort`, then captures the arguments passed to `findAll`. It does not try to inspect the internals of the generated specification. That behavior is already covered by the JPA test. The MVC test's job is to prove that Spring binds the request and that the controller calls the repository with a specification and the expected `Pageable`.

That split is intentional. Repository tests should use a real database, even an embedded one, because specification bugs are usually SQL-shape bugs: the wrong comparison, the wrong case handling, or a predicate that should have disappeared but did not. MVC tests should stay narrow. Once the controller has passed a specification and pageable object to the repository, the rest of the behavior belongs to the data layer.

Run the chapter tests from the Maven project:

```bash
./mvnw -q -B test
```

## Run It

Start the application from the chapter directory:

```bash
./mvnw spring-boot:run
```

Then try different combinations of query parameters:

```bash
curl "http://localhost:8080/products?page=0&size=10&sort=createdAt,desc"
```

```bash
curl "http://localhost:8080/products?q=run&page=0&size=10&sort=name"
```

```bash
curl "http://localhost:8080/products?category=bags&minPrice=5000&maxPrice=8000&inStock=true&sort=priceCents"
```

```bash
curl "http://localhost:8080/products?category=gear&inStock=true&page=0&size=2&sort=createdAt,desc"
```

Those requests all hit the same endpoint and the same repository method. Only the composed specification changes.

## Limits

Specifications are a strong default for dynamic predicates over entities. They are much better than hand-built JPQL strings for ordinary search forms, and they keep each filter testable in isolation.

They are not the perfect tool for every query. Projections can be awkward because `JpaSpecificationExecutor` is centered on entity results. Very complex `IN` clauses, correlated subqueries, vendor-specific SQL, and report-style result shapes can require extra Criteria API ceremony. When a search builder grows into a query language of its own, look at Querydsl for a type-safe predicate DSL or jOOQ when you want SQL-shaped control.

For typical application screens, though, specifications hit a useful middle ground: small reusable predicates, clean optional-filter handling, and the same `Pageable` and `Sort` support you already use with Spring Data repositories.

Chapter 23 moves from querying rows to tracking them with JPA auditing.
