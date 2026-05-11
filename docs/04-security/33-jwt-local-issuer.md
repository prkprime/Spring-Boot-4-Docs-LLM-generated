# 33. JWT Local Demo Issuer (non-production)

!!! danger "This chapter is for offline learning only"
    The code below stitches together a tiny JWT issuer so you can see, end to end, how a `client_credentials` exchange produces a signed access token and how the same Spring Boot app then accepts that token as a resource server. **Do not use this in production.** Real applications point Spring Security at a real Identity Provider — chapter 35 swaps Keycloak in via Testcontainers.

Chapter 32 showed the consumer side: validate JWTs that arrive in `Authorization: Bearer …` headers. The producer (the IdP) was someone else's problem. This chapter closes the loop by minting tokens locally so you can experiment without an external IdP.

We deliberately **roll our own** issuer with [Nimbus JOSE+JWT](https://connect2id.com/products/nimbus-jose-jwt) — the JWT library that ships with `spring-boot-starter-oauth2-resource-server` — instead of using Spring Authorization Server. Spring Authorization Server is the right answer for any non-trivial deployment, but its configuration surface is large and it eclipses what we want to show: every byte of the token round-trip.

## What we'll build

- A startup-generated **RSA keypair**, exposed as a JWK Set at `GET /jwks`.
- A **`POST /token`** endpoint that authenticates a hard-coded client (`demo-client` / `demo-secret`), then signs and returns an RS256 JWT with `sub`, `iss`, `aud`, `iat`, `exp`, and `scope` claims.
- The same app acts as the **resource server**: `oauth2ResourceServer().jwt()` validates incoming tokens against the keypair we just exposed.
- An integration test that obtains a token from `/token`, then calls `/api/me` with `Authorization: Bearer …` and asserts the subject and scopes flow through.

## The token round-trip

```
client                    issuer (this app)              resource server (this app)
  |                              |                                   |
  |  POST /token (form body)     |                                   |
  | ---------------------------> |                                   |
  |                              | sign RS256 JWT                    |
  |  200 OK { access_token: ...} |                                   |
  | <--------------------------- |                                   |
  |                                                                  |
  |  GET /api/me                                                     |
  |  Authorization: Bearer <jwt>                                     |
  | ---------------------------------------------------------------> |
  |                                              verify signature    |
  |                                              against /jwks       |
  |  200 OK { subject, scopes }                                      |
  | <--------------------------------------------------------------- |
```

In a real system the issuer and resource server are different processes (often different organizations). Here they are the same JVM only so we don't need a second project to demonstrate the flow.

## Security configuration

The filter chain enables `oauth2ResourceServer().jwt()` and **explicitly** permits the `/token`, `/jwks`, and `/api/public` endpoints. Everything else requires authentication. The session is stateless and CSRF is off — both correct for a token-authenticated API.

```java
{% include-markdown "../../code/33-jwt-local-issuer/maven/src/main/java/dev/springboot4docs/ch_33_jwt_local_issuer/SecurityConfig.java" comments=false %}
```

The keypair beans are the heart of the chapter:

- `KeyPair rsaKeyPair()` runs at startup, generates a fresh 2048-bit RSA key. Real systems persist their keys outside the JVM; we deliberately do not.
- `RSAKey rsaJwk(KeyPair)` wraps the keypair as a Nimbus `RSAKey` with a random `kid` (key id). Tokens carry this `kid` in their header so verifiers know which key to use.
- `JWKSource<SecurityContext>` is the source of truth Nimbus needs to sign tokens. `ImmutableJWKSet` keeps the key set fixed for the JVM's lifetime — fine for a demo, terrible for production where keys must rotate.
- `JwtDecoder` is what Spring Security's resource-server pipeline calls to verify each incoming token. We build it from the same public key, so the signature check round-trips.

## The `/token` endpoint

```java
{% include-markdown "../../code/33-jwt-local-issuer/maven/src/main/java/dev/springboot4docs/ch_33_jwt_local_issuer/TokenController.java" comments=false %}
```

Step by step:

1. Validate client credentials. Constant-time comparison would be sensible in a real implementation; this demo uses `equals` because the credentials are public anyway.
2. Build a `JWTClaimsSet` with the standard claims — `sub` (the client id), `iss` (issuer; we use `"self"` because the issuer URI is whatever localhost happens to bind to in the test), `aud` (audience; matched against the resource server during validation), `iat`, `exp` (one hour out), and a custom `scope` claim.
3. Wrap the claims in a `SignedJWT`, sign with RSASSA-PKCS1-v1_5 + SHA-256 (`RS256`), serialize to the compact JWS form, and return it in the OAuth2 token-response shape: `{access_token, token_type, expires_in, scope}`.
4. The companion `GET /jwks` returns the **public** half of the same key as a JSON Web Key Set. A real resource server elsewhere would call this URL via the `jwk-set-uri` configuration property to find our verification key.

## The protected API

`/api/me` injects the authenticated `Jwt` via `@AuthenticationPrincipal` and returns its subject and scope claims. Once the resource server accepts a bearer token, Spring puts a `JwtAuthenticationToken` (whose principal is the `Jwt`) in the security context.

```java
{% include-markdown "../../code/33-jwt-local-issuer/maven/src/main/java/dev/springboot4docs/ch_33_jwt_local_issuer/ApiController.java" comments=false %}
```

`/api/public` is open to anonymous callers; it exists only so a passing test can confirm the filter chain doesn't accidentally lock down public endpoints.

## End-to-end test

The integration test runs the full app on a random port, hits `/token` to obtain a real JWT, then uses that JWT against `/api/me`. There are no mocks, no fake decoders — the same code paths a real client would use.

```java
{% include-markdown "../../code/33-jwt-local-issuer/maven/src/test/java/dev/springboot4docs/ch_33_jwt_local_issuer/LocalIssuerIntegrationTest.java" comments=false %}
```

Three test methods exercise the flow:

- `tokenCanBeUsedAtProtectedApi` — POST `/token`, capture the access token, GET `/api/me` with it, assert `subject == demo-client` and `scopes[0] == read`.
- `protectedApiRejectsAnonymousRequests` — GET `/api/me` without a token returns `401`.
- `publicApiAllowsAnonymousRequests` — GET `/api/public` returns `200` to anyone.

## Run it

```bash
cd code/33-jwt-local-issuer/maven
./mvnw spring-boot:run
```

In another terminal:

```bash
# obtain a token
TOKEN=$(curl -s -X POST http://localhost:8080/token \
              -d 'client_id=demo-client&client_secret=demo-secret&scope=read' | jq -r .access_token)

# call the protected API
curl -s http://localhost:8080/api/me -H "Authorization: Bearer $TOKEN"
# {"subject":"demo-client","scopes":["read"]}

# inspect the public verification keys
curl -s http://localhost:8080/jwks | jq .
```

Paste the access token into [jwt.io](https://jwt.io) to see the header and claims human-readably.

## Why you should not ship this

A real OAuth2 / OpenID Connect issuer does work this code does not:

- **Authorization grants** beyond `client_credentials`: authorization code with PKCE for browsers, refresh tokens for long-lived sessions, device code for input-constrained devices.
- **Discovery**: `/.well-known/openid-configuration` advertises every endpoint.
- **User identity**: real flows have a user database, login UI, MFA, password reset, account lockout, audit log, federated login.
- **Key management**: persistent storage, scheduled rotation, dual-key advertisement during rotation, hardware-backed signing.
- **Token introspection and revocation**: `/oauth2/introspect`, `/oauth2/revoke`.
- **Audience binding**: tokens scoped to specific resource servers.
- **Consent and scopes UI**.
- **Compliance**: OIDC certification, security review, CVE patching.

If your reaction is "we'll just add those" — close this tab and use Keycloak, Okta, Auth0, or Authentik. The next chapter walks through the Keycloak-via-Testcontainers pattern, which is what production code actually does.

## What's next

Chapter 34 introduces the **client** side of OAuth2 — "Sign in with Google/GitHub" — using Spring's `oauth2Login()` pipeline. Chapter 35 then revisits the resource-server pattern with a real Keycloak instance, finally treating identity as a separate system rather than something we build ourselves.
