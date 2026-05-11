# JWT Resource Server

JWT resource server is the production side of bearer-token security. Your application does not ask for a password, does not show a login page, and does not mint access tokens. It receives an HTTP request with an `Authorization: Bearer ...` header, validates that token, turns selected claims into Spring Security authorities, and then applies the same authorization rules you already know.

That vocabulary matters:

- A **resource server** hosts protected APIs. This chapter is a resource server.
- An **authorization server** authenticates users and issues tokens. In production, that is usually an external IdP such as Keycloak, Spring Authorization Server, Auth0, Okta, Azure AD, or another OpenID Connect provider.
- A **client** calls the API. It may be a SPA, mobile app, server-side app, CLI, or another service.

!!! warning "This chapter does not issue tokens"
    This sample validates JWTs that were issued elsewhere. Chapter 33 builds a local demo issuer so you can see token creation, but that issuer is deliberately non-production. Production systems should validate tokens from a real authorization server or IdP.

The production pattern is simple: configure where the resource server can find the IdP's public signing keys, then keep authorization local to your API. Token issuance belongs to the IdP. Business authorization still belongs to your application.

## Dependencies

The sample uses Spring MVC, Spring Security, and the OAuth2 resource-server starter:

```xml
{% include-markdown "../../code/32-jwt-resource-server/maven/pom.xml" comments=false %}
```

The resource-server starter brings in the JWT support that Spring Security needs to parse bearer tokens and verify their signatures.

## Production Configuration

The key production knob is `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`:

```yaml
{% include-markdown "../../code/32-jwt-resource-server/maven/src/main/resources/application.yml" comments=false %}
```

A JWK Set URI points to the authorization server's public JSON Web Key Set. The IdP signs access tokens with a private key. The resource server downloads the matching public keys from this endpoint and uses them to verify the JWT signature. If the signature does not verify, the token is rejected before your controller runs.

Spring Boot auto-configures a `NimbusJwtDecoder` from this property. You do not create a decoder by hand for the common case. You configure the IdP endpoint and let Boot wire the decoder.

The sample URI uses `.invalid` on purpose. It documents the property shape without pretending there is a real IdP behind the sample. In a deployed application, this value must come from your authorization server configuration, not from the API team guessing a URL. The resource server and the IdP need to agree on issuer, signing keys, algorithms, token lifetime, and which claims represent permissions.

Most production applications use the alternative `issuer-uri` property instead:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://idp.example.com/realms/orders
```

With `issuer-uri`, Spring discovers the provider metadata, finds the JWKS URI, and validates the issuer value as part of token validation. That is usually less brittle than hard-coding the JWKS endpoint, as long as the IdP exposes a standard OpenID Connect or OAuth2 authorization-server metadata document. This chapter uses `jwk-set-uri` because it makes the moving part visible.

For a real service, prefer `issuer-uri` first. Reach for `jwk-set-uri` when discovery is unavailable, blocked by network policy, or intentionally managed outside the application.

## The Pipeline

A JWT request moves through the same Spring Security filter chain as the earlier chapters, but the authentication step is different:

1. The client sends `Authorization: Bearer <token>`.
2. `BearerTokenAuthenticationFilter` extracts the bearer token.
3. `JwtDecoder` verifies the JWT signature against the IdP's public keys and checks standard token validity.
4. `JwtAuthenticationConverter` turns JWT claims into granted authorities.
5. Spring Security creates a JWT-backed authentication token and stores it in the `SecurityContext`.
6. Your URL rules, method security, and controller code read the normal authenticated principal.

The important split is decoder versus converter. The decoder decides whether the token is real and valid. The converter decides what the token means inside this application.

By default, Spring Security reads the `scope` or `scp` claim and maps values to `SCOPE_` authorities. A token with:

```json
{
  "sub": "alice",
  "scope": "read write"
}
```

receives `SCOPE_read` and `SCOPE_write`. That is why resource-server authorization rules commonly look like `hasAuthority("SCOPE_orders:read")` rather than `hasRole(...)`.

Real IdPs often add their own claim shapes. Some use `roles`, some use nested realm or client-role claims, and some use groups. The production move is not to abandon the default scope mapping. Keep it, then add your application-specific mapping beside it.

## Security Configuration

The sample security configuration is a stateless API:

```java
{% include-markdown "../../code/32-jwt-resource-server/maven/src/main/java/dev/springboot4docs/ch_32_jwt_resource_server/SecurityConfig.java" comments=false %}
```

The first decision is CSRF. This API authenticates every protected request with an `Authorization` header, not a browser session cookie, so CSRF protection is disabled. That is the same reasoning from chapter 31: CSRF is for defending cookie-authenticated browser requests from forged cross-site writes. A stateless bearer-token API has a different credential model.

The session policy is `STATELESS`. A resource server should not create an HTTP session just because a JWT was accepted. The next request must present a valid bearer token again.

The URL rules are intentionally small. `/public/**` is open. `/api/**` requires authentication. `/api/admin/**` requires `SCOPE_admin`, so the admin route must be declared before the broader `/api/**` matcher. Authorization matchers are evaluated in order; put narrower rules first.

The resource-server line installs JWT bearer-token authentication:

```java
.oauth2ResourceServer((resourceServer) -> resourceServer
		.jwt((jwt) -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
```

If you did not need custom authority mapping, `jwt(Customizer.withDefaults())` would be enough. This chapter does customize authorities, so the configuration passes in the `JwtAuthenticationConverter` bean explicitly.

The converter keeps the default scope behavior by delegating to `JwtGrantedAuthoritiesConverter`. Then it reads a `roles` claim and maps values to `ROLE_` authorities. A token with `roles: ["ADMIN"]` therefore receives `ROLE_ADMIN`, while a token with `scope: "read admin"` still receives `SCOPE_read` and `SCOPE_admin`.

That distinction is useful. Scopes usually describe what the client was allowed to request from the authorization server. Roles or groups usually describe the user or service account. Your application can use both, but it should be deliberate about which claim owns which decision.

## API Controller

The controller has one public route and two protected routes:

```java
{% include-markdown "../../code/32-jwt-resource-server/maven/src/main/java/dev/springboot4docs/ch_32_jwt_resource_server/ApiController.java" comments=false %}
```

`GET /public/info` proves that public routes still work with the security chain enabled.

`GET /api/me` injects the authenticated `Jwt` with `@AuthenticationPrincipal`. The controller returns the subject and the raw scope values so you can see the token identity that made it through the filter chain. In a real API, you usually keep controllers focused on business data and let authorization rules inspect authorities. This endpoint is intentionally introspective because it is a teaching sample.

`GET /api/admin/secrets` returns a fixed string, but the route is protected by the URL rule in `SecurityConfig`. A JWT with `scope: "admin"` is accepted because the default scope converter grants `SCOPE_admin`. A JWT with only `scope: "read"` is authenticated but forbidden.

Authentication and authorization are separate outcomes:

- No bearer token on `/api/me` means `401 Unauthorized`.
- A valid bearer token without `SCOPE_admin` on `/api/admin/secrets` means `403 Forbidden`.
- A valid bearer token with the required authority means `200 OK`.

## Tests

The tests use the Spring Boot 4 MVC slice and `MockMvcTester`:

```java
{% include-markdown "../../code/32-jwt-resource-server/maven/src/test/java/dev/springboot4docs/ch_32_jwt_resource_server/JwtResourceServerWebMvcTest.java" comments=false %}
```

The important testing pattern is `jwt()` from Spring Security's MockMvc support:

```java
with(jwt().jwt((token) -> token
		.subject("alice")
		.claim("scope", "read")))
```

That request post-processor puts a JWT-backed authentication into the mock request's security context. The test does not need a real private key, a real JWKS endpoint, or a `NimbusJwtDecoder` pointed at a fake URL. You are testing your web security rules and controller behavior, not the IdP.

The nested test decoder exists only so the MVC slice can build the resource-server filter chain. It throws if it is accidentally used. That keeps the tests honest: successful test requests are using `jwt()`, not decoding real bearer strings.

That is a useful boundary for unit-style MVC tests. You should have confidence that `/api/admin/secrets` requires `SCOPE_admin`, but you do not need every controller slice to prove that Nimbus can download a key set. Put decoder and IdP integration coverage in a smaller number of integration tests, preferably against the same kind of provider you use in production.

The test cases cover the boundary:

- `/public/info` accepts anonymous traffic and returns `200`.
- `/api/me` rejects anonymous traffic with `401`.
- `/api/me` accepts a mock JWT with subject `alice`.
- `/api/admin/secrets` accepts `scope: "admin"`.
- `/api/admin/secrets` rejects `scope: "read"` with `403`.
- The converter maps `roles: ["ADMIN"]` to `ROLE_ADMIN`.

The assertions use `.hasStatus(401)`, `.hasStatus(403)`, and `.hasStatus(200)` for numeric status checks. Do not use nonexistent helpers such as `.hasStatusUnauthorized()`.

Run the chapter tests from the code directory:

```bash
./mvnw -q -B test
```

## Production Notes

Key rotation is part of the JWKS design. The authorization server publishes public keys at the JWK Set URI, and the resource server can refresh its cached keys as the IdP rotates signing keys. Spring Security's Nimbus integration uses a JWK set cache for this instead of fetching keys on every request. You should still monitor IdP availability and failed-token rates, but you should not hard-code public keys into application code.

Clock skew is the tolerance for small differences between the IdP clock and the API server clock when validating `exp`, `nbf`, and `iat` claims. Keep server clocks synchronized with NTP. If you need a small tolerance, configure the JWT decoder rather than adding time hacks in controllers. For custom decoder setup, Spring Security's Nimbus decoder builder supports clock-skew configuration.

Audience validation is another common production requirement. A token issued for `payments-api` should not be accepted by `orders-api` just because the signature is valid. Add an `OAuth2TokenValidator<Jwt>` bean or decoder customizer that checks the `aud` claim and attach that validator to the decoder. Signature validity answers "did a trusted issuer sign this?" Audience validation answers "was this token meant for this API?"

Do not trust authorization decisions that only live in the client. A SPA can hide an admin button, but the resource server still needs the `SCOPE_admin` or role check. JWTs make identity portable across services; they do not move authorization responsibility out of the API.

Do not put authorization secrets in JWTs. A JWT is signed, not necessarily encrypted. Clients can usually read its claims. Put stable identifiers, scopes, roles, groups, and token metadata in the token. Keep sensitive application data behind your API.

!!! warning "Local token issuers are for demos"
    Chapter 33 shows a local issuer so the mechanics of signing a JWT are visible. That is useful for learning, local demos, and tests. It is not a replacement for an authorization server with user authentication, client registration, key rotation, revocation strategy, discovery metadata, audit logs, and operational ownership.

## What to Remember

A Spring Boot resource server validates tokens; it does not issue them. Configure `jwk-set-uri` or, more commonly, `issuer-uri`, and let Spring Boot create the decoder.

JWT authentication has two major extension points. `JwtDecoder` validates the token. `JwtAuthenticationConverter` maps claims to Spring Security authorities. Keep the default `scope`/`scp` to `SCOPE_` mapping unless you have a strong reason to remove it, then add your IdP-specific role or group mapping beside it.

In MVC tests, use Spring Security's `jwt()` request post-processor. It gives you deterministic tests for resource-server authorization without running a fake IdP or publishing test JWKS documents.
