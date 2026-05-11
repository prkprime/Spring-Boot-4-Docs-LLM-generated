# Method Security

URL security is the first authorization boundary most Spring applications meet. It is also not the only boundary that matters. A request path can say "this endpoint needs a user," but the service method often knows the real business rule: only admins can delete, only the owner can read this record, only auditors can see the audit log, and only matching owners should survive a filtering operation.

Method security puts those rules next to the methods that enforce them. That gives you defense in depth. The web layer can still reject obviously wrong requests early, while the service layer protects the operation even when it is called from a controller, a scheduled job, a message listener, or another internal adapter. In this chapter the URL layer is deliberately open so the method annotations are the only gate. That is useful for learning and testing the feature. In production, use both layers.

The main annotations are small but expressive:

- `@PreAuthorize` evaluates a SpEL expression before the method runs. This is the most common method-security annotation.
- `@PostAuthorize` evaluates after the method returns and can inspect `returnObject`. Use it when the authorization decision depends on the loaded result.
- `@PreFilter` filters an input collection before the method sees it.
- `@PostFilter` filters a returned collection before the caller receives it.
- `@Secured` is the older role-only annotation. It still works when enabled.
- `@RolesAllowed` is the JSR-250 annotation. It works when JSR-250 support is enabled.

Those expressions use the same basic ideas as URL authorization. `hasRole('ADMIN')` checks for `ROLE_ADMIN`. `hasAuthority('SCOPE_book:write')` checks for an exact authority string and does not add a role prefix. You can refer to `principal`, `authentication`, method arguments such as `#id` or `#username`, and special values such as `returnObject` in post-invocation checks.

## Dependencies

The sample uses Spring MVC, Spring Security, and the MVC/security test starters:

```xml
{% include-markdown "../../code/30-method-security/maven/pom.xml" comments=false %}
```

No database is needed. The service keeps a tiny in-memory catalog so the chapter can focus on authorization behavior.

## Security Configuration

Method security is enabled on a normal configuration class:

```java
{% include-markdown "../../code/30-method-security/maven/src/main/java/dev/springboot4docs/ch_30_method_security/SecurityConfig.java" comments=false %}
```

`@EnableMethodSecurity` enables `@PreAuthorize` and `@PostAuthorize` by default through `prePostEnabled = true`. This chapter also sets `securedEnabled = true` for `@Secured` and `jsr250Enabled = true` for `@RolesAllowed`.

The filter chain is intentionally permissive:

```java
.authorizeHttpRequests((requests) -> requests.anyRequest().permitAll())
```

That line means every URL is reachable at the HTTP layer. HTTP Basic is still enabled so you can authenticate as `alice`, `bob`, or `carol`, but the URL matcher does not decide who may delete, read, audit, or export. The service methods decide.

The users are simple on purpose. `alice` has `USER`, `bob` has `USER` and `ADMIN`, and `carol` has `USER` and `AUDITOR`. Spring Security stores roles as authorities with a `ROLE_` prefix, so Bob's admin role becomes `ROLE_ADMIN` and Carol's auditor role becomes `ROLE_AUDITOR`.

## Domain Records

The sample domain is just two Java records:

```java
{% include-markdown "../../code/30-method-security/maven/src/main/java/dev/springboot4docs/ch_30_method_security/Book.java" comments=false %}
```

```java
{% include-markdown "../../code/30-method-security/maven/src/main/java/dev/springboot4docs/ch_30_method_security/AuditEvent.java" comments=false %}
```

`Book.owner` is the important field. Several method-security expressions compare that owner to `authentication.name`.

## Service Rules

The service contains one method for each annotation style:

```java
{% include-markdown "../../code/30-method-security/maven/src/main/java/dev/springboot4docs/ch_30_method_security/BookService.java" comments=false %}
```

`deleteBook(...)` uses:

```java
@PreAuthorize("hasRole('ADMIN')")
```

The expression runs before the method body. A caller without `ROLE_ADMIN` receives `403 Forbidden`, and the method is not invoked.

`writeBook(...)` uses:

```java
@PreAuthorize("hasAuthority('SCOPE_book:write')")
```

This shows the difference between roles and authorities. `hasRole('ADMIN')` is role-oriented and adds the `ROLE_` prefix for you. `hasAuthority('SCOPE_book:write')` checks the exact authority value. OAuth2 resource servers often use authorities such as `SCOPE_book:write`, but the point is general: not every authorization rule has to be a role check.

`getBook(...)` uses:

```java
@PostAuthorize("returnObject.owner == authentication.name")
```

The method runs first, returns a `Book`, and then the expression compares the returned book's owner with the authenticated username. This pattern is useful when you cannot know whether access is allowed until after loading the row. In a real database-backed service, you would usually prefer a query such as "find by id and owner" when possible, because it avoids loading data that will be rejected. `@PostAuthorize` is still useful for compact examples and for cases where the result is already being assembled for other reasons.

`importBooks(...)` uses `@PreFilter`. Spring Security walks the incoming collection and removes elements that do not match:

```java
@PreFilter("filterObject.owner == authentication.name")
```

The method only receives books owned by the current principal. `filterObject` is the current collection element.

`findAll()` uses `@PostFilter`:

```java
@PostFilter("filterObject.owner == authentication.name")
```

The service returns the catalog, then Spring Security filters the returned list before the controller sees it. This is easy to demonstrate, but it is not a pagination strategy. Do not load a huge table into memory and then use `@PostFilter` as your data-access predicate. Push the owner predicate into the repository query when the data set is large or paginated.

`auditLog()` uses the older `@Secured("ROLE_AUDITOR")`. Unlike `hasRole`, this annotation takes the full role authority string, including `ROLE_`. `exportBooks()` uses `@RolesAllowed({ "ADMIN", "AUDITOR" })`, where the role names are written without the `ROLE_` prefix.

## Controller

The controller has no authorization logic of its own:

```java
{% include-markdown "../../code/30-method-security/maven/src/main/java/dev/springboot4docs/ch_30_method_security/BookController.java" comments=false %}
```

Each endpoint delegates directly to `BookService`. Because the URL layer permits every request, any `403 Forbidden` response from these endpoints comes from method security. That makes the chapter's behavior easy to see in tests.

This is also the composition model to use in real systems, even when the URL layer is not open. A common setup is URL security for broad routing rules and method security for business rules. For example, `/admin/**` can require an admin at the web layer, while `deleteBook(...)` still requires `ADMIN` at the service layer. If a future controller accidentally exposes deletion through a different path, the service method still protects itself.

## Tests

The tests use `@WebMvcTest`, import the real security configuration and service, and then call the controller through `MockMvcTester`:

```java
{% include-markdown "../../code/30-method-security/maven/src/test/java/dev/springboot4docs/ch_30_method_security/MethodSecurityWebMvcTest.java" comments=false %}
```

`@WithMockUser` installs an authenticated user in the test security context. `roles = "USER"` creates `ROLE_USER`; `roles = { "USER", "ADMIN" }` creates both `ROLE_USER` and `ROLE_ADMIN`. That is why the delete test with Alice receives `403`, while the delete test with Bob receives `200`.

The owner tests show `@PostAuthorize`. Alice can fetch `/books/1` because that book is owned by `alice`. The same user receives `403` for `/books/2` because the method returns Bob's book and the post-authorization expression rejects it.

The `findAll` test shows `@PostFilter`. The service has four books, but Alice owns two of them, so the returned JSON array length is `2`. The caller never receives Bob's or Carol's books.

The audit and export tests show the older annotation styles. Carol's `AUDITOR` role satisfies `@Secured("ROLE_AUDITOR")`; Alice's `USER` role does not. Both Bob's `ADMIN` role and Carol's `AUDITOR` role satisfy `@RolesAllowed({ "ADMIN", "AUDITOR" })`, while Alice is rejected.

Run the chapter tests from the code directory:

```bash
./mvnw -q -B test
```

The assertions intentionally use `hasStatus(401)` and `hasStatus(403)` for numeric status checks. `MockMvcTester` does not provide `hasStatusUnauthorized()` or `hasStatusForbidden()` helpers.

## Run It

Start the sample:

```bash
./mvnw spring-boot:run
```

Then call the same endpoint with different users:

```bash
curl -i -u alice:secret -X DELETE http://localhost:8080/books/1
curl -i -u bob:secret -X DELETE http://localhost:8080/books/1
curl -i -u alice:secret http://localhost:8080/books/1
curl -i -u alice:secret http://localhost:8080/books/2
curl -i -u carol:secret http://localhost:8080/audit
```

The first delete returns `403` because Alice is not an admin. Bob's delete succeeds because he has `ADMIN`. Alice can read book `1`, but not book `2`, because `@PostAuthorize` checks the loaded book's owner after the service method returns. Carol can read the audit log because she has `AUDITOR`.

## Principal Consistency

Method security and URL security evaluate against the same authenticated principal. That is usually what you want, but it exposes a common design problem: double authentication. If your web layer authenticates one principal and your service method receives a separate username, tenant id, or account id from request data, check that they match before trusting the request value.

Prefer expressions that refer to method arguments explicitly:

```java
@PreAuthorize("#username == authentication.name")
```

Do not build SpEL strings by concatenating user input. That is expression injection. Keep the expression static and pass request values as method parameters, then refer to them with `#parameterName`.

## Footguns

Method security is proxy-based. Self-invocation bypasses the proxy: if one method on `BookService` calls another method on the same instance, the inner call does not pass through the method-security interceptor. Put protected operations on a separate bean, or call them through a properly proxied collaborator.

Filtering annotations operate on collections that have already been materialized. `@PreFilter` can be useful for small command lists, and `@PostFilter` is handy for small returned collections or teaching examples. They are not replacements for SQL predicates, repository methods, row-level security, or search filters.

Finally, keep broad and narrow rules aligned. If the URL layer says a user can reach a route, but the service layer says the method is admin-only, the user gets a confusing `403`. That may be correct, but it should be intentional. In production code, document which layer owns each kind of rule: coarse request routing at the URL boundary, business authorization at the method boundary.

Chapter 31 stays in the security stack and moves back to HTTP concerns: CSRF, response headers, CORS, and how those protections interact.
