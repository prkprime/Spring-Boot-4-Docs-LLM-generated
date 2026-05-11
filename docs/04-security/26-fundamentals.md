# Spring Security 7 Fundamentals

Spring Security is easiest to learn if you start with the request path. In a Spring MVC application, a request does not go straight to your controller. It first passes through the servlet filter chain, and Spring Security installs its own filter chain into that path.

```text
request
  -> SecurityFilterChain
       -> authentication filters
       -> authorization filter
       -> other security filters
       -> DispatcherServlet
            -> controller
       <- DispatcherServlet
  <- security filters finish
response
```

The important object is `SecurityFilterChain`. It is a list of servlet filters that wrap the request before Spring MVC's `DispatcherServlet` chooses a controller. Authentication happens in one of those filters. With HTTP Basic, that work is handled by `BasicAuthenticationFilter`, which reads the `Authorization` header and turns valid credentials into an authenticated principal. Authorization happens later in `AuthorizationFilter`, the modern request-authorization filter that replaces the older `FilterSecurityInterceptor` mental model for this part of the stack.

The authenticated user is stored in the Spring Security context for the duration of the request. In servlet applications that context is normally bound to the current request thread. The security filters set it before your controller runs and clear it when the filter chain exits. That cleanup matters because servlet containers reuse threads. A principal from request A must never leak into request B.

That is the mental model to keep through the whole security section. Authentication answers "who is this request?" Authorization answers "is that authenticated request allowed to do this?" They are related, but not the same operation. A request can authenticate successfully as `alice` and still be denied access to an admin endpoint. A request can also skip authentication entirely for a public endpoint because the authorization rule says anonymous access is allowed.

Spring Security has many filters because HTTP security has many jobs: reading credentials, saving or clearing the security context, writing security headers, enforcing CSRF protection, handling logout, translating access-denied exceptions into HTTP responses, and more. You do not usually assemble those filters by hand. You describe policy through the `HttpSecurity` DSL, and Spring Security builds the chain.

## Dependency

The chapter project includes Web MVC, Spring Security, and the matching test starters:

```xml
{% include-markdown "../../code/26-fundamentals/maven/pom.xml" comments=false %}
```

Adding `spring-boot-starter-security` is enough to turn security on. If you write no security configuration at all, Spring Boot puts every endpoint behind authentication, enables HTTP Basic, creates a default user named `user`, and prints a generated random password to the startup log. That is useful for the first encounter because it proves the dependency is active immediately. It is not a default to keep. Real applications should define explicit URL rules and explicit users or identity-provider integration.

The generated-password default is intentionally noisy. You will see a line in the startup output telling you the password for the default user. That is convenient for running `curl -u user:... http://localhost:8080/me` while learning, but it is also a sign that the application has not made a real authentication decision yet. Once you add an `InMemoryUserDetailsManager`, JDBC users, LDAP, OAuth2 login, or a JWT resource server, that temporary default should disappear from the design.

## The Small App

The controller has three endpoints:

```java
{% include-markdown "../../code/26-fundamentals/maven/src/main/java/dev/springboot4docs/ch_26_fundamentals/HelloController.java" comments=false %}
```

`GET /` and `GET /public/info` return fixed public strings. `GET /me` returns `principal.getName()`, which means the endpoint only makes sense after authentication has happened. The controller does not parse HTTP Basic credentials and does not inspect headers. It trusts the security filter chain to establish the `Principal` before the request reaches MVC.

This separation is the first practical payoff of the filter-chain model. The controller can stay focused on application behavior. It receives a `Principal` because security has already done the protocol work. If the request is unauthenticated and the endpoint requires authentication, the controller method is not the place that rejects it. The filter chain rejects it before the controller runs.

## SecurityFilterChain DSL

This is the active security configuration for the sample:

```java
{% include-markdown "../../code/26-fundamentals/maven/src/main/java/dev/springboot4docs/ch_26_fundamentals/SecurityConfig.java" comments=false %}
```

`@EnableWebSecurity` imports Spring Security's servlet infrastructure. Spring Boot can auto-configure a default chain, but once you provide your own `SecurityFilterChain` bean, your bean becomes the application policy.

The `@Bean SecurityFilterChain` method takes `HttpSecurity`. Think of `HttpSecurity` as a builder for the filters and shared security objects used by this application. Calling `.build()` freezes that configuration into the chain that will run for matching servlet requests. Most applications define one chain. Advanced applications can define multiple chains, each scoped with its own matcher, but one chain is the right starting point.

The lambda DSL is the normal Spring Security 7 style:

```java
http.authorizeHttpRequests((authorize) -> authorize
		.requestMatchers("/", "/public/**", "/actuator/health").permitAll()
		.anyRequest().authenticated())
```

Use `authorizeHttpRequests`, not the old `authorizeRequests`. Use `requestMatchers(...)`, including overloads such as `requestMatchers(HttpMethod.GET, "/foo")` when the HTTP method is part of the rule. The old `mvcMatchers` style is gone in Spring Security 7.

Rule order matters. Spring Security evaluates request authorization rules in the order you write them. The first matching rule wins. That is why the public paths come first and the catch-all rule comes last. If you put `.anyRequest().authenticated()` before a later `permitAll()` rule, the later rule is never reached. If you forget the catch-all rule entirely, paths that do not match your explicit rules can be left public.

In this chapter, three paths are public. `/` is useful for a landing endpoint. `/public/**` shows a path segment convention. `/actuator/health` is included because health checks often need to be reachable by load balancers or orchestration platforms. The project does not need to do anything special with that path in the controller; the rule is here to show where a public operational endpoint would fit.

The configuration also enables HTTP Basic:

```java
http.httpBasic(Customizer.withDefaults())
```

This gives the sample a small, testable authentication mechanism. The same shape is used for other security features. You will commonly see:

```java
http.formLogin(Customizer.withDefaults());
http.httpBasic(Customizer.withDefaults());
http.csrf((csrf) -> ...);
http.headers((headers) -> ...);
http.sessionManagement((sessions) -> ...);
```

Each call configures one part of the filter chain. Form login adds browser login behavior and session integration. HTTP Basic adds header-based authentication. CSRF, headers, and session management tune protections that live around your controller, not inside it.

Leaving form login out of this sample is intentional. A browser login page would add another workflow before the reader has the core model. HTTP Basic keeps the request visible: the client sends credentials in an `Authorization` header, the authentication filter verifies them, and a protected endpoint either returns the principal name or responds with `401 Unauthorized`.

## Users And Passwords

The second bean in `SecurityConfig` is an `InMemoryUserDetailsManager`. It creates one user, `alice`, with the password `secret`:

```java
PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
UserDetails alice = User.withUsername("alice")
		.password(encoder.encode("secret"))
		.roles("USER")
		.build();
```

Three Spring Security abstractions are worth naming early:

- `AuthenticationManager` coordinates an authentication attempt.
- `AuthenticationProvider` knows how to authenticate one kind of credential.
- `UserDetailsService` loads user records by username.

`InMemoryUserDetailsManager` is a simple `UserDetailsService` implementation. It is good for demos and tests because the users are defined in code. The next chapters keep exercising `UserDetailsService`, then replace the in-memory store with more realistic backing data.

There is one subtle ownership rule here: define one source of users. If you create your own `UserDetailsService` bean, Boot's generated user is no longer the useful default. If you later add a database-backed service, remove or replace the in-memory bean rather than leaving multiple user stores competing for the same job. Security startup failures caused by "helpful" duplicate beans are easier to avoid than to debug.

The password encoder is also deliberate. `PasswordEncoderFactories.createDelegatingPasswordEncoder()` returns a delegating encoder that stores the algorithm id in the encoded value. Encoding `"secret"` produces a value shaped like this:

```text
{bcrypt}$2a$10$...
```

The `{bcrypt}` prefix tells Spring Security which encoder should verify the password later. The delegating encoder can also understand ids such as `{scrypt}`, `{argon2}`, and `{noop}` when configured for those formats. In a real application, store encoded passwords, never raw passwords. In this demo, the raw string exists only long enough to create the in-memory user at startup.

The prefix also gives you a migration path. Older accounts might have one encoding id, while newly changed passwords use a stronger default. On login, the delegating encoder can read the prefix and verify the stored value with the matching algorithm. Chapter 29 uses this idea more seriously when passwords move out of code and into persistent storage.

## Opening Everything

The active sample secures most routes. The smallest explicit unauthenticated application is a different `SecurityFilterChain`:

```java
@Bean
SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
	return http
			.authorizeHttpRequests((authorize) -> authorize
					.anyRequest().permitAll())
			.build();
}
```

That is useful for proving a point: adding the security starter does not force every application to require login forever. The application policy is whatever chain you define. Still, be explicit. A deliberate `.anyRequest().permitAll()` is clearer than accidentally leaving endpoints open because the final authorization rule was forgotten.

A stricter variant for APIs is to end with `.anyRequest().denyAll()` while you are building out rules. That makes new endpoints fail closed until you decide where they belong. For this chapter we use `.authenticated()` because the goal is to show one public area and one protected area.

There is also a difference between "permit every request" and "remove Spring Security from the app." The `permitAll()` chain still lets other security behavior exist if you configure it: headers, CSRF rules, exception handling, and test support can still participate. That is why an explicit chain is often better than trying to exclude the security auto-configuration. You keep the security system visible and make the access rule obvious.

## Tests

The tests use the Spring Boot Web MVC test slice, import the real security configuration, and drive requests through `MockMvcTester`:

```java
{% include-markdown "../../code/26-fundamentals/maven/src/test/java/dev/springboot4docs/ch_26_fundamentals/HelloControllerWebMvcTest.java" comments=false %}
```

The first two tests prove the allow-list:

```text
/              -> 200
/public/info   -> 200
```

Those requests do not include credentials. They pass because the first authorization rule permits `/` and `/public/**`.

The protected endpoint is intentionally just as small. A complicated response body would distract from the security behavior being tested. The body is the principal name, so a successful response proves both parts of the path: authentication produced a principal, and authorization allowed the request through to MVC.

The next test calls `/me` with no user and expects `401 Unauthorized`. That status comes from the security filter chain before the controller can return a name. There is no `Principal`, so the request is challenged.

The test class uses `@WebMvcTest(HelloController.class)` because the endpoint behavior is at the web layer. It imports `SecurityConfig` so the slice uses the real authorization rules and the real in-memory user. Without that import, the test would not prove the chapter's security configuration. The point is not just that the controller returns a string; it is that the request has to pass through the same security decisions the application uses.

`@WithMockUser("alice")` is a Spring Security test shortcut. It places an authenticated user in the security context for that test method, so the request behaves as if authentication had already succeeded. This is useful when the test is about authorization or controller behavior and the credential mechanism is not the subject.

The HTTP Basic tests exercise the real credential path:

```java
this.mvc.get().uri("/me")
		.with(httpBasic("alice", "secret"))
```

`httpBasic(...)` comes from `SecurityMockMvcRequestPostProcessors`. `MockMvcTester` can use those same MockMvc request post-processors through `.with(...)`. With the right password, `BasicAuthenticationFilter` authenticates `alice`, the controller receives that principal, and the body is `alice`. With the wrong password, authentication fails and the response is `401`.

Spring Security's test support also has helpers such as `.with(authentication(...))` when you want to provide a specific `Authentication` object. Use `@WithMockUser` for compact tests, `httpBasic(...)` when the header-based login path matters, and `authentication(...)` when you need exact authorities or principal details.

The combination is useful. One test checks the controller with an already-authenticated principal. Two tests check the actual HTTP Basic credentials. That avoids a common blind spot where every test uses `@WithMockUser` and the application accidentally breaks the login mechanism itself.

Run the tests from the chapter project:

```bash
./mvnw -q -B test
```

## Common Footguns

Forgetting the final rule is the easiest mistake. A security chain that only says `requestMatchers("/public/**").permitAll()` does not communicate what should happen to everything else. End with `.anyRequest().authenticated()` for the normal secure default, or `.anyRequest().denyAll()` when you want new endpoints to fail closed.

Putting `permitAll()` after `anyRequest()` is another ordering bug. The catch-all matcher consumes the request first, so the later rule is dead code. Keep specific paths first and broad paths last.

Do not mix Boot's generated default user with your own `UserDetailsService` bean. Once you define your own users, the generated password flow is no longer the source of truth. In a real app, make the ownership obvious: either use Boot's temporary default during first startup, or define the real authentication source.

Do not copy old matcher names into Spring Security 7 code. `mvcMatchers` is gone. Use `requestMatchers(...)`, and include `HttpMethod` when a rule should only apply to one method.

Finally, keep security rules close to the HTTP surface. Controllers should express application behavior. The security chain should express which requests are public, which requests require authentication, and which authentication mechanisms are enabled.

Chapter 27 keeps HTTP Basic but spends more time on users, roles, and credentials. This chapter gave you the map: request filters first, controller second, explicit rules in order, and tests that prove both public and protected paths.
