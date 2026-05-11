# Part IV — Security Ladder

Eleven chapters that climb from "lock the API with HTTP Basic" to "validate JWTs from a real OAuth2 IdP." Spring Security 7 is the same `SecurityFilterChain`-based programming model as recent SB3 versions, just modernized.

By the end of Part IV you will have:

- A correct mental model of the Spring Security filter chain.
- Working examples of HTTP Basic, form login + sessions, database-backed users with password upgrades, method security, CSRF/headers, JWT (both resource-server validation and a clearly-non-production local issuer), OAuth2 social login, OAuth2 resource server with Keycloak via Testcontainers, and LDAP.

Passkeys / WebAuthn — which needs its own browser-side ceremony, HTTPS, and recovery story — gets a dedicated **Part VII** mini-project rather than a single rung here.

Chapters:

26. [Spring Security 7 Fundamentals](26-fundamentals.md)
27. [HTTP Basic + In-Memory Users](27-http-basic.md)
28. [Form Login + Sessions](28-form-login.md)
29. [JDBC Users + Password Encoders](29-jdbc-users.md)
30. [Method Security](30-method-security.md)
31. [CSRF, Headers, CORS-Security Interplay](31-csrf-headers.md)
32. [JWT Resource Server](32-jwt-resource-server.md)
33. [JWT Local Demo Issuer](33-jwt-local-issuer.md) (non-production)
34. [OAuth2 Login](34-oauth2-login.md)
35. [OAuth2 Resource Server with Keycloak](35-oauth2-keycloak.md)
36. [LDAP Authentication](36-ldap.md)
