# JDBC Users And Password Encoders

In-memory users are useful while you are learning the shape of Spring Security. They are also useful in small tests where the whole user store should fit on the screen. Real accounts usually need a database. Users need to survive restarts, support password resets, be disabled when employment or subscription status changes, and fit into operational workflows such as audits, support tooling, and account recovery.

The moment accounts become operational data, hard-coded users become a liability. A password reset cannot wait for a redeploy. A support engineer needs to disable a compromised account without editing Java code. A compliance review needs to know when an account was created and whether it is still active. Those requirements are not exotic; they are the normal shape of account lifecycle management. Moving users to a database is the first step toward treating accounts as data owned by the application instead of constants owned by configuration.

This chapter moves the user store out of configuration and into H2 through Spring Data JPA. The title says JDBC because, at runtime, authentication data is coming from a relational database over JDBC. We use JPA as the persistence API so the sample can stay close to the data chapters that came before it. The security mechanism stays familiar: HTTP Basic is still the login protocol because it is easy to test. The difference is where Spring Security gets the user and password hash.

The important abstraction is `UserDetailsService`. Spring Security calls `loadUserByUsername(...)` during authentication. Your application decides whether that username comes from memory, JDBC, JPA, LDAP, an HTTP service, or something else. This chapter backs it with a normal JPA repository.

## Dependencies

The project uses Spring MVC, Spring Security, Spring Data JPA, H2, and the MVC/security/JPA test starters:

```xml
{% include-markdown "../../code/29-jdbc-users/maven/pom.xml" comments=false %}
```

There is no Flyway migration in this chapter. In production, use migrations. Here we use Hibernate `ddl-auto: update` so the chapter can focus on Spring Security's user-store integration instead of schema management.

## The User Table

The entity is deliberately small:

```java
{% include-markdown "../../code/29-jdbc-users/maven/src/main/java/dev/springboot4docs/ch_29_jdbc_users/AppUser.java" comments=false %}
```

`username` is unique because authentication needs one account per login name. `passwordHash` stores an encoded password, never the raw password. `roles` is a comma-separated string such as `USER,ADMIN`; that is not a full authorization model, but it keeps the database shape easy to see. `enabled` lets the account be disabled without deleting it, and `createdAt` gives us a basic lifecycle timestamp.

A larger application might normalize roles into a separate table, add audit columns, track password-reset state, or split account profile data from login credentials. Those are product and data-model decisions. The authentication bridge only needs enough information to build a `UserDetails`.

There is one important simplification here: `AppUser` is package-private and uses package-private accessors because this chapter is not designing a public domain API. The controllers, repository, tests, and security service live in the same package. That keeps the example compact while still using a real entity, real table, and real repository.

The repository is ordinary Spring Data JPA:

```java
{% include-markdown "../../code/29-jdbc-users/maven/src/main/java/dev/springboot4docs/ch_29_jdbc_users/AppUserRepository.java" comments=false %}
```

`findByUsername(...)` is the query Spring Security needs. It returns `Optional<AppUser>` so a missing user can become a `UsernameNotFoundException` in the security layer.

Index this column in a real database. The `unique` constraint shown on the entity is enough for the generated H2 schema, but production DDL should explicitly create the unique index through a migration. Authentication lookup is on the hot path of every login attempt, including failed attempts, so it should not depend on a table scan.

## UserDetailsService

The JPA-backed service is the seam between your account table and Spring Security:

```java
{% include-markdown "../../code/29-jdbc-users/maven/src/main/java/dev/springboot4docs/ch_29_jdbc_users/JpaUserDetailsService.java" comments=false %}
```

`loadUserByUsername(...)` loads the row and converts it to Spring Security's built-in `User` implementation. The username and password hash are copied directly. The `enabled` flag becomes `disabled(...)`, so a disabled account cannot authenticate even if the password is correct.

The role conversion is the one naming rule to watch. Spring Security's role checks use authorities with a `ROLE_` prefix. A database value of `USER` becomes `ROLE_USER`; `ADMIN` becomes `ROLE_ADMIN`. That keeps the table readable while still matching Spring Security conventions such as `.hasRole("ADMIN")`.

Do not put password verification in this service. `UserDetailsService` loads identity data; the authentication provider verifies credentials. That separation is what lets the same user-loading code work with HTTP Basic, form login, or another username/password mechanism. If a username is missing, throw `UsernameNotFoundException`. If the username exists, return the stored hash and account flags, then let `DaoAuthenticationProvider` compare the presented password with the encoder.

This service also implements `UserDetailsPasswordService`. That is the password-upgrade hook. When authentication succeeds, `DaoAuthenticationProvider` asks the configured `PasswordEncoder` whether the stored hash should be upgraded. If it should, Spring Security calls `updatePassword(user, newHash)`. The service updates the database and returns a fresh `UserDetails`.

This is the sanctioned way to silently rotate users to a stronger password encoder. Do not write a custom login filter that updates hashes by hand. Let successful authentication prove that the user knows the old password, then let Spring Security provide the new encoded value.

## Password Encoder Format

The password encoder bean uses Spring Security's delegating encoder:

```java
PasswordEncoderFactories.createDelegatingPasswordEncoder()
```

It stores passwords with an algorithm id prefix:

```text
{bcrypt}$2a$10$...
```

That prefix is a feature. It lets the application verify old hashes and write new hashes with the current default. A database can contain `{bcrypt}`, `{noop}` in a test fixture, or another supported id. During login, `DelegatingPasswordEncoder` reads the prefix and chooses the matching verifier.

The default encoder writes bcrypt hashes. If a user signs up today, the stored password starts with `{bcrypt}`. If an old row still contains a legacy format, the application can upgrade it the next time the user logs in successfully. That avoids a risky all-at-once migration where every account must be rehashed before users can sign in.

The prefix also prevents ambiguity. Without it, the application has to infer the algorithm from string length, marker characters, or migration dates. That kind of guessing is fragile. With `{id}hash`, the stored value is self-describing, and the encoder can reject unknown ids instead of quietly applying the wrong verifier.

## Security Configuration

The full security configuration wires the pieces together:

```java
{% include-markdown "../../code/29-jdbc-users/maven/src/main/java/dev/springboot4docs/ch_29_jdbc_users/SecurityConfig.java" comments=false %}
```

The URL rules keep this chapter focused:

```java
.requestMatchers("/login", "/signup", "/public/**").permitAll()
.anyRequest().authenticated()
```

Anyone can sign up. Everything else, including `/api/me`, requires authentication. HTTP Basic stays enabled because it makes the tests straightforward and keeps the chapter about storage rather than browser login mechanics.

The `DaoAuthenticationProvider` is the important bean. It receives the JPA `UserDetailsService`, the delegating `PasswordEncoder`, and the same service as `UserDetailsPasswordService`. On each login, the provider loads the user, verifies the presented password against the stored hash, and upgrades the stored hash when the encoder says the value is outdated.

That wiring is the difference between "I can load users from a table" and "my real login path is using the table correctly." If the provider is not using the same encoder that signup uses, new users may be saved successfully and then fail to authenticate. If the provider is not given a `UserDetailsPasswordService`, legacy hashes may continue to work but never rotate forward.

The configuration also exposes an `AuthenticationManager`. Many simple applications do not need to inject it directly, but registration flows, token endpoints, or custom authentication entry points sometimes do. Keeping the bean explicit makes the authentication graph easy to inspect.

CSRF is disabled here for the same reason it was disabled in the HTTP Basic API chapter: these tests post JSON directly and do not model browser cookies. Chapter 31 returns to CSRF and response headers as first-class security concerns.

## Signup Flow

The signup controller accepts a tiny JSON request:

```java
{% include-markdown "../../code/29-jdbc-users/maven/src/main/java/dev/springboot4docs/ch_29_jdbc_users/SignupController.java" comments=false %}
```

There are no validation niceties here because chapter 8 already covered Bean Validation. The important line is:

```java
this.passwordEncoder.encode(request.password())
```

The raw password only exists at the boundary of the request. Before the user is saved, it becomes an encoded hash with an `{id}` prefix. The new account receives the `USER` role, which becomes `ROLE_USER` when loaded by `JpaUserDetailsService`.

This controller does not check for duplicate usernames, enforce password strength, confirm email addresses, or add a CAPTCHA. Those are real signup requirements, but they would distract from the security-store mechanics. The invariant this chapter cares about is narrow and non-negotiable: a submitted password must be encoded before persistence.

The API endpoint is intentionally small:

```java
{% include-markdown "../../code/29-jdbc-users/maven/src/main/java/dev/springboot4docs/ch_29_jdbc_users/ApiController.java" comments=false %}
```

`/api/me` returns the authenticated principal name. It gives the tests a protected endpoint that proves the database-backed user can complete the real authentication path.

## Application Configuration

The application uses an in-memory H2 database:

```yaml
{% include-markdown "../../code/29-jdbc-users/maven/src/main/resources/application.yml" comments=false %}
```

`ddl-auto: update` asks Hibernate to create or adjust the schema from the entity model. That is acceptable for this follow-along sample. Production applications should use versioned migrations with Flyway or Liquibase so schema changes are reviewed, repeatable, and reversible.

The sample also seeds an admin user outside the test profile:

```java
{% include-markdown "../../code/29-jdbc-users/maven/src/main/java/dev/springboot4docs/ch_29_jdbc_users/SeedUsers.java" comments=false %}
```

The seeded account is `admin` with password `secret` and roles `USER,ADMIN`. It uses the same configured encoder as signup, so the database stores a `{bcrypt}` value. The `test` profile disables the seed runner so tests can control the user table exactly.

## Tests

The tests load the real application context, real security chain, real JPA repository, and MockMvcTester:

```java
{% include-markdown "../../code/29-jdbc-users/maven/src/test/java/dev/springboot4docs/ch_29_jdbc_users/JdbcUsersSecurityTests.java" comments=false %}
```

The first test checks the baseline: anonymous access to `/api/me` returns `401 Unauthorized`. That proves the endpoint is protected by the filter chain, not by controller code.

The signup test drives `POST /signup` through MVC instead of inserting directly through the repository. That matters because the controller owns password encoding. After signup, the test checks that the stored value starts with `{bcrypt}`, then calls `/api/me` with `httpBasic("ada", "secret")`. A `200 OK` response with body `ada` proves the user can authenticate from the database.

The password-upgrade test is the pattern to remember. It inserts a user with a legacy hash:

```text
{noop}plain
```

That value is intentionally weak and only belongs in tests. The user then logs in once with HTTP Basic. The login succeeds because `{noop}` tells the delegating encoder how to verify the stored value. After success, Spring Security notices the hash is not using the current default encoder and calls `UserDetailsPasswordService.updatePassword(...)`. The test reloads the row and asserts that the stored hash now starts with `{bcrypt}`.

That test is high value because it proves three things at once: legacy hashes still authenticate, the upgrade hook is wired into the provider, and the database is updated after successful login.

Run the chapter tests from the project directory:

=== "Maven"
    ```bash
    ./mvnw test
    ```

=== "Gradle"
    ```bash
    ./gradlew test
    ```

## Run It

Start the app:

=== "Maven"
    ```bash
    ./mvnw spring-boot:run
    ```

=== "Gradle"
    ```bash
    ./gradlew bootRun
    ```

Create a user:

```bash
curl -i -X POST http://localhost:8080/signup \
  -H 'Content-Type: application/json' \
  -d '{"username":"ada","password":"secret"}'
```

Then authenticate:

```bash
curl -i -u ada:secret http://localhost:8080/api/me
curl -i -u admin:secret http://localhost:8080/api/me
```

Both requests return the authenticated username when the credentials are valid.

## Production Notes

Never store plaintext passwords. Never store MD5 or SHA-1 password hashes. Fast general-purpose hashes are the wrong tool for passwords because attackers can try them at enormous scale. Use a password hashing algorithm intended to be slow and salted.

Bcrypt is Spring Security's default delegating choice and is a strong default for many applications. Argon2 is also a good choice when you can take the dependency and tune memory cost appropriately. Avoid scrypt unless you have a specific operational reason and have tuned it deliberately.

Password hashing is only one layer. Account lockout, rate limiting, breached-password checks, CAPTCHA or bot defenses, MFA, audit logs, and recovery flows are product requirements that need explicit design. Spring Security gives you hooks, events, and extension points, but it does not turn those policies into a complete account system by itself.

At this point, the application can authenticate users from a database and upgrade password hashes as users log in. Chapter 30 builds on that by moving authorization decisions closer to service methods with method security.
