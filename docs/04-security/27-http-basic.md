# HTTP Basic And In-Memory Users

HTTP Basic is the smallest authentication mechanism you can see directly in an HTTP request. The client sends this header on each protected request:

```text
Authorization: Basic base64(username:password)
```

There is no login page and no application session required for the client to become authenticated. Every request carries credentials, Spring Security reads the header, validates the username and password, and creates an authenticated principal for that request.

That makes HTTP Basic easy to test with `curl`, scripts, build agents, and small internal tools. It is a poor default for user-facing browser authentication. Basic authentication has no friendly logout workflow, no built-in account recovery, no MFA story, and no rate limiting by itself. If a human will sign in through a browser, use a browser-oriented mechanism such as form login, OAuth2 login, or an identity provider flow. Use HTTP Basic when the tradeoff is intentional: internal APIs, machine-to-machine calls, prototypes, and examples where clarity matters more than user experience.

This chapter uses HTTP Basic to protect a tiny API and adds role-based access at the URL level.

The important behavior is visible in the response status codes. A public request returns `200 OK` without credentials. A protected request without credentials returns `401 Unauthorized`, which means "authenticate and try again." A request from an authenticated user who lacks the required role returns `403 Forbidden`, which means "we know who you are, and you still cannot do this." Keep those two failures separate in your tests. They catch different classes of security mistakes.

## Dependencies

The project includes Spring MVC, Spring Security, and the MVC/security test starters:

```xml
{% include-markdown "../../code/27-http-basic/maven/pom.xml" comments=false %}
```

Adding `spring-boot-starter-security` turns on the servlet security infrastructure. The application still needs an explicit `SecurityFilterChain` so the public, user, and admin areas are visible in code.

## URL Rules

Here is the complete security configuration:

```java
{% include-markdown "../../code/27-http-basic/maven/src/main/java/dev/springboot4docs/ch_27_http_basic/SecurityConfig.java" comments=false %}
```

Read the authorization rules from top to bottom:

```java
.requestMatchers("/admin/**").hasRole("ADMIN")
.requestMatchers("/api/**").authenticated()
.requestMatchers("/", "/public/**").permitAll()
.anyRequest().denyAll()
```

`/admin/**` is the most specific protected area, so it appears first. A request to `/admin/secrets` must have the `ADMIN` role. `/api/**` is broader: any authenticated user can call it. `/` and `/public/**` are deliberately open to anonymous requests. The final `anyRequest().denyAll()` makes unknown paths fail closed.

Order matters. Spring Security evaluates these matchers in the order you write them, and the first matching rule wins. If `/api/**` came before `/admin/**`, then `/admin/secrets` would still not match `/api/**`, but in a real application overlapping patterns are common: `/api/admin/**` must be before `/api/**`. Keep the specific paths first and the catch-all rule last.

The chain enables HTTP Basic with:

```java
.httpBasic(Customizer.withDefaults())
```

That installs the Basic authentication filter. When credentials are valid, the request continues to Spring MVC with an authenticated `Authentication` in the security context. When credentials are missing or wrong for a protected endpoint, the request is rejected with `401 Unauthorized` before a controller method runs.

This chapter also disables CSRF:

```java
.csrf((csrf) -> csrf.disable())
```

That is a teaching choice for this API-only sample. CSRF protection is a real security decision with implications, especially for browser clients and cookie-backed sessions. Chapter 31 turns CSRF back on and explains how to reason about it instead of treating it as boilerplate.

## Users And Roles

The same configuration defines two users with an `InMemoryUserDetailsManager`:

- `alice` has role `USER`.
- `bob` has roles `ADMIN` and `USER`.

Both use the demo password `secret`, encoded with Spring Security's delegating password encoder:

```java
PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
```

The encoded value includes an algorithm id, such as `{bcrypt}`, so Spring Security knows which password encoder should verify the stored value later. Even in a small example, keep the habit: store encoded passwords, not raw passwords.

`InMemoryUserDetailsManager` is useful for docs, demos, and tests because the whole user store is visible in one file. It is not where production accounts usually belong. Chapter 29 keeps the same Spring Security shape but swaps the in-memory users for a JDBC-backed user store.

There is one role naming detail to remember. In configuration, `.hasRole("ADMIN")` checks for the authority `ROLE_ADMIN`. When you build a user with `.roles("ADMIN", "USER")`, Spring Security adds that `ROLE_` prefix for you. Use roles for coarse application areas like `USER` and `ADMIN`; use authorities directly when you need finer permissions.

This chapter keeps authorization at the URL level. That is a good first tool because the rules are easy to scan and they run before MVC calls a controller. A request that is not allowed never reaches the method body. Later chapters can add method-level security when the decision depends on service-layer data, but broad areas such as `/admin/**` and `/api/**` usually belong near the HTTP entry point.

## Controllers

The public controller exposes the anonymous endpoints:

```java
{% include-markdown "../../code/27-http-basic/maven/src/main/java/dev/springboot4docs/ch_27_http_basic/PublicController.java" comments=false %}
```

Those methods do not know anything about authentication because their URL rules say authentication is not required.

The API controller returns the authenticated user's name:

```java
{% include-markdown "../../code/27-http-basic/maven/src/main/java/dev/springboot4docs/ch_27_http_basic/ApiController.java" comments=false %}
```

`Authentication` is method-injected by Spring MVC after the security filter chain has established the request's principal. If an anonymous request calls `/api/me`, the controller is not responsible for rejecting it. The security chain handles that first.

The admin controller is deliberately boring:

```java
{% include-markdown "../../code/27-http-basic/maven/src/main/java/dev/springboot4docs/ch_27_http_basic/AdminController.java" comments=false %}
```

The interesting behavior is not the string it returns. The interesting behavior is that only a user with `ROLE_ADMIN` can reach it.

## Tests

The tests use Spring Boot's Web MVC slice and import the real security configuration:

```java
{% include-markdown "../../code/27-http-basic/maven/src/test/java/dev/springboot4docs/ch_27_http_basic/HttpBasicSecurityWebMvcTest.java" comments=false %}
```

`@WebMvcTest` loads the selected controllers and MVC test infrastructure. `@Import(SecurityConfig.class)` brings in the actual chapter security chain and in-memory users, so the tests exercise the same URL rules as the running application.

The public tests send anonymous requests to `/` and `/public/info` and expect `200 OK`. That proves the `permitAll()` rules are not accidentally hidden behind a broader authenticated rule.

The API tests show both sides of HTTP Basic. An anonymous request to `/api/me` returns `401 Unauthorized`. A request with valid Basic credentials succeeds:

```java
this.mvc.get().uri("/api/me")
		.with(httpBasic("alice", "secret"))
```

`httpBasic(...)` is a Spring Security MockMvc request post-processor. `MockMvcTester` can use it through `.with(...)`, which keeps the test close to the real HTTP shape without manually encoding the `Authorization` header.

The admin tests separate authentication from authorization. `@WithMockUser(username = "alice", roles = "USER")` creates an authenticated user for the test request, but that user still lacks `ADMIN`, so `/admin/secrets` returns `403 Forbidden`. `bob` authenticates with HTTP Basic and has `ADMIN`, so the same endpoint returns `200 OK`.

Using both helpers is intentional. `@WithMockUser` is compact when the test is about authorization only: "given an authenticated user with these roles, is the URL allowed?" `httpBasic(...)` is better when the credential mechanism itself matters: "can this username and password pass through the Basic authentication filter?" A healthy security test suite usually has both styles. If every test uses mock users, the URL rules may be covered while the real login path is broken. If every test uses real credentials, simple authorization cases become noisier than they need to be.

Run the tests from the chapter project:

```bash
./mvnw -q -B test
```

## Run It

Start the application:

```bash
./mvnw spring-boot:run
```

The public endpoints work without credentials:

```bash
curl -i http://localhost:8080/
curl -i http://localhost:8080/public/info
```

The protected API endpoint challenges anonymous callers:

```bash
curl -i http://localhost:8080/api/me
```

Send Basic credentials with `-u`:

```bash
curl -i -u alice:secret http://localhost:8080/api/me
curl -i -u alice:secret http://localhost:8080/admin/secrets
curl -i -u bob:secret http://localhost:8080/admin/secrets
```

`alice` can call `/api/me`, but receives `403 Forbidden` from `/admin/secrets`. `bob` can call both because he has both roles.

This is the core HTTP Basic pattern: choose URL rules, define users, enable Basic authentication, and test the difference between anonymous, authenticated, and authorized requests. Chapter 28 builds on that by keeping the same security-chain mindset while adding the next security feature.
