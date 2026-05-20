# OAuth2 Resource Server with Keycloak

Chapter 33 built a tiny local issuer so you could see the moving parts: an authorization server signs a JWT, the API validates it, and Spring Security creates an authenticated principal from the token. That chapter is useful because it removes the external identity provider while you learn the shape.

This chapter puts the external identity provider back. We will boot a real Keycloak server in Docker with Testcontainers, import a repeatable realm configuration, ask Keycloak for access tokens, and make the Spring Boot app validate those tokens as an OAuth2 resource server.

That is the grown-up resource-server loop. The application does not sign its own tokens. It trusts a provider by issuer URI, downloads that provider's OpenID Connect metadata and keys, validates the bearer token, and then authorizes the request from claims in that token.

A real identity provider changes what the test is allowed to prove. A hand-written issuer can prove that your controller accepts a bearer token, but it cannot prove that your application agrees with the provider about issuer URLs, signing keys, token endpoints, username claims, or role shape. Those are the details that fail when an application moves from a local demo to a shared identity system. Bringing Keycloak into the test means the sample exercises the same discovery and JWT validation path that a deployed resource server uses.

There is still a boundary. This is not a Keycloak administration chapter, and it is not a complete identity-platform operations guide. We keep the realm small so the Spring Boot side stays visible: one realm, one public client, two users, one realm role, and one resource server that maps that role into a Spring Security authority.

## Dependencies

The application is still a Spring MVC API with Spring Security and the resource-server starter. The test side adds Testcontainers and the Keycloak container module from the `dasniko/testcontainers-keycloak` project.

```xml
{% include-markdown "../../code/35-oauth2-keycloak/maven/pom.xml" comments=false %}
```

This chapter needs Docker. The test starts a real Keycloak process, imports a realm JSON file, obtains tokens from Keycloak's token endpoint, then calls the Spring Boot app with those tokens. That takes longer than an MVC slice test, but it proves much more of the real contract.

## Keycloak in 60 Seconds

Keycloak is an open-source identity provider. It supports OpenID Connect and SAML, has a web admin console, can manage users and roles, and can be self-hosted. Many teams also run it through an operator or through a hosted service built around Keycloak.

For this chapter, Keycloak has four pieces of vocabulary that matter:

| Term | Meaning in this sample |
| --- | --- |
| Realm | A security boundary. Our realm is `sb4-docs`. Its issuer URL ends with `/realms/sb4-docs`. |
| Client | An application registered with the realm. Our test client is `spring-boot-app`. |
| User | A person or account that can authenticate. The realm has `alice` and `bob`. |
| Role | A named permission. The realm has an `admin` role, assigned only to `bob`. |

The Spring Boot application is not a Keycloak client in the browser-login sense from chapter 34. It is a resource server. It receives bearer tokens that were issued by Keycloak and validates them before allowing access to `/api/**`.

That distinction is worth keeping sharp. OAuth2 Login is about the application sending a user to a provider and creating a browser session when the user returns. A resource server is about accepting API calls with bearer tokens. The resource server usually does not know how the user authenticated. It cares whether the token is valid, whether it was issued by the expected issuer, whether it is intended for this API, and whether its claims authorize the requested action.

## Application Configuration

The app's only OAuth2 resource-server property is the issuer URI:

```yaml
{% include-markdown "../../code/35-oauth2-keycloak/maven/src/main/resources/application.yml" comments=false %}
```

In local development, that default points at `http://localhost:8080/realms/sb4-docs`. In the test, Keycloak runs on a random host port, so the test replaces the property dynamically. Spring Security uses the issuer URI to discover the OpenID Connect provider metadata and the JSON Web Key Set used to verify token signatures.

The important production habit is to configure the issuer, not a random JWKS URL copied from an admin screen. The issuer URI gives Spring Security enough information to validate issuer consistency and to find the provider's keys through standard metadata.

The issuer must match the `iss` claim inside the token exactly. This is the reason Testcontainers configuration matters. Keycloak may be listening on port 8080 inside the container, while the test process reaches it through something like `http://localhost:32781`. If the token says one issuer and the application is configured with another, validation should fail. The test uses the container's host-facing URL so Keycloak's metadata, token issuer, and Spring Security configuration agree.

## The API

The sample API has one normal authenticated endpoint and one admin endpoint:

```java
{% include-markdown "../../code/35-oauth2-keycloak/maven/src/main/java/dev/springboot4docs/ch_35_oauth2_keycloak/ApiController.java" comments=false %}
```

`/api/me` returns the JWT subject, Keycloak's `preferred_username`, and any realm roles present in the token. `/api/admin/secrets` returns a simple string so the test can focus on authorization behavior.

The controller accepts `Jwt` with `@AuthenticationPrincipal`. At that point the token has already been decoded, its signature has been checked, the issuer has been checked, and Spring Security has created an authenticated `JwtAuthenticationToken`.

## Security Configuration

The security chain is the same resource-server shape from chapter 32, but the role mapping is Keycloak-specific:

```java
{% include-markdown "../../code/35-oauth2-keycloak/maven/src/main/java/dev/springboot4docs/ch_35_oauth2_keycloak/SecurityConfig.java" comments=false %}
```

The URL policy is intentionally small:

```text
/api/me              authenticated
/api/admin/**        ROLE_admin
everything else      denied
```

The call to `oauth2ResourceServer((resourceServer) -> resourceServer.jwt(Customizer.withDefaults()))` enables JWT bearer-token support. With an `issuer-uri` property, Boot supplies the `JwtDecoder` that knows how to use Keycloak's metadata and signing keys.

The subtle part is authorities. Spring Security's default JWT authority mapping reads OAuth2 scopes from `scope` or `scp` and turns them into authorities such as `SCOPE_read`. Keycloak realm roles are not stored there. A Keycloak access token usually stores them under `realm_access.roles`:

```json
{
  "preferred_username": "bob",
  "realm_access": {
    "roles": [
      "admin"
    ]
  }
}
```

The `JwtAuthenticationConverter` bean reads that nested claim and turns each role into a Spring Security authority. The `admin` Keycloak role becomes `ROLE_admin`, so the request matcher can use `hasAuthority("ROLE_admin")`.

Spring Security 7's resource-server JWT configuration will use a `JwtAuthenticationConverter` bean from the application context when one is present. That lets the chain keep the simple `jwt(Customizer.withDefaults())` shape while still teaching Spring how this identity provider represents roles.

The converter deliberately uses `hasAuthority` rather than `hasRole`. Both can work, but `hasRole("admin")` adds the `ROLE_` prefix implicitly. In a chapter about claim mapping, spelling out `hasAuthority("ROLE_admin")` makes the final authority string visible. That removes a common source of confusion when a token contains `admin`, a converter emits `ROLE_admin`, and the authorization rule checks a third spelling.

## A Reproducible Realm

The test imports a realm JSON file instead of clicking around in the Keycloak admin console:

```json
{% include-markdown "../../code/35-oauth2-keycloak/maven/src/test/resources/keycloak/sb4-docs-realm.json" comments=false %}
```

The realm is named `sb4-docs`. It defines one public OpenID Connect client named `spring-boot-app`. The client has direct access grants enabled because the test uses the password grant to obtain tokens without a browser.

That password grant is a test convenience, not a login pattern to design a new user-facing application around. It keeps the integration test compact: send username and password to Keycloak's token endpoint, receive an access token, then call the API with `Authorization: Bearer <token>`.

The users are:

| User | Password | Realm roles |
| --- | --- | --- |
| `alice` | `secret` | none |
| `bob` | `secret` | `admin` |

This gives the test two meaningful tokens. Alice can authenticate, but she is not an admin. Bob can authenticate and pass the admin rule.

Realm imports are also easy to review. When a test fails because a role disappeared or a client setting changed, the fix is a code review against a JSON file rather than a hunt through a mutable admin console. That is the main reason to prefer imported realm state in samples and integration tests.

## Keycloak as Test Infrastructure

The Testcontainers configuration exposes a `KeycloakContainer` bean:

```java
{% include-markdown "../../code/35-oauth2-keycloak/maven/src/test/java/dev/springboot4docs/ch_35_oauth2_keycloak/TestcontainersConfiguration.java" comments=false %}
```

The container imports `/keycloak/sb4-docs-realm.json` from the test classpath. Each test run starts from the same realm state, which is the real value of this setup. You are not depending on whatever happens to be configured in a developer's local Keycloak instance.

The static `issuerUri` helper starts the container and returns the mapped host URL for the realm. Keycloak listens inside Docker, but the Spring Boot test process must reach it through the host and random mapped port. That is why the application cannot use the fallback `localhost:8080` issuer during the test.

The first call to the helper starts Keycloak before Spring needs the issuer property. Starting the same container again through Spring's container lifecycle is harmless because Testcontainers treats an already-running container as running. The useful part is that the issuer supplier can safely ask the container for its mapped URL.

## Dynamic Properties

The integration test wires the real issuer into Spring Boot before the application context finishes starting:

```java
@DynamicPropertySource
static void keycloakProperties(DynamicPropertyRegistry registry) {
	registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
			() -> TestcontainersConfiguration.issuerUri(REALM));
}
```

`@DynamicPropertySource` is the bridge between a dynamic Testcontainers environment and normal Spring Boot configuration. The application code still reads the ordinary `spring.security.oauth2.resourceserver.jwt.issuer-uri` property. The test decides the actual value after Docker chooses the mapped port.

That distinction is important. Avoid hard-coding container ports in application code. Keep the application normal and let the test environment provide dynamic configuration.

## The Integration Test

This is a full HTTP integration test. The Spring Boot app starts on a random port, Keycloak starts in Docker, and the test uses real HTTP calls on both sides:

```java
{% include-markdown "../../code/35-oauth2-keycloak/maven/src/test/java/dev/springboot4docs/ch_35_oauth2_keycloak/KeycloakResourceServerTest.java" comments=false %}
```

The `accessToken` helper posts form data to Keycloak:

```text
grant_type=password
client_id=spring-boot-app
username=alice
password=secret
```

Keycloak returns a JSON response with an `access_token`. The test then calls the Spring Boot app with:

```text
Authorization: Bearer <access token>
```

Three scenarios matter:

1. Alice calls `/api/me`, the resource server validates the Keycloak token, and the endpoint returns her username.
2. Alice calls `/api/admin/secrets`, authentication succeeds but authorization fails with `403 Forbidden`.
3. Bob calls `/api/admin/secrets`, the role converter maps Keycloak's `admin` role to `ROLE_admin`, and the endpoint returns `admin secrets`.

This is the confidence jump from chapter 33. The app is no longer validating tokens minted by itself. It is validating tokens from an external OIDC provider, using the provider's metadata and signing keys, with roles shaped the way that provider actually emits them.

Notice what the test does not mock. It does not mock `JwtDecoder`, it does not use `jwt()` from Spring Security's MVC test support, and it does not fabricate authorities in the request. Those are good techniques for narrower controller tests, but this chapter is checking the provider boundary. If the realm import, token endpoint, issuer URI, decoder, role converter, or authorization rule is wrong, this test has a chance to catch it.

Run it from the chapter directory:

=== "Maven"
    ```bash
    ./mvnw test
    ```

=== "Gradle"
    ```bash
    ./gradlew test
    ```

The first run may take a little while because Docker has to pull and start Keycloak. After that, the test still costs more than an MVC slice, but it exercises the provider boundary that tends to break in production.

## Production Notes

Do not treat the test realm JSON as your production Keycloak operations model. Realm imports are excellent for repeatable tests and local demos. Production Keycloak is usually managed through the admin API, declarative infrastructure, the Keycloak operator, or a controlled promotion process.

Run Keycloak like infrastructure, not like a throwaway sidecar. Use a real database, persist it, back it up, run more than one instance where your availability requirements demand it, and put it behind TLS. Your resource servers depend on the issuer metadata and keys being available, and your users depend on the identity provider being healthy.

Pay attention to audiences. Many providers can issue tokens that are valid for more than one consumer unless you configure them carefully. In Keycloak, audience mappers are commonly used so an access token intended for your API contains the correct `aud` claim. The resource server should then validate that audience.

Spring Security lets you add audience validation with a custom `OAuth2TokenValidator<Jwt>` on the `JwtDecoder`. The issuer check answers "who issued this token?" Audience validation answers "was this token meant for this API?" Production APIs usually need both.

Also decide where authorization belongs. Realm roles are easy to demonstrate, but large systems often use client roles, groups, permissions from an authorization service, or application-owned policy. The converter in this chapter is deliberately small so the Keycloak claim shape is visible.

Chapter 36 continues the security ladder with LDAP, which is a different identity integration style: directory-backed users instead of bearer tokens from an OAuth2/OIDC provider.
