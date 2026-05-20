# LDAP Authentication

LDAP is the legacy enterprise authentication source that never quite went away. Many organizations still keep employee identity in LDAP or Active Directory, and applications are expected to authenticate against that directory instead of owning their own password table.

This chapter builds a small Spring Boot API that authenticates HTTP Basic credentials against an LDAP directory. The directory is not an external service you have to install. Spring Boot starts an embedded UnboundID LDAP server and loads a tiny LDIF file, so the sample and tests are repeatable.

The goal is not to become an LDAP administrator. The goal is to understand enough of the directory shape to configure Spring Security's LDAP authentication provider: where users live, how to search for a username, where groups live, and how group membership becomes a Spring Security authority.

Teams still use LDAP because identity data already lives there. A company may have years of employee lifecycle automation connected to Active Directory: hiring creates an account, department moves update groups, and termination disables the account. In that environment, a Spring Boot application should not invent a second employee directory. It should ask the existing directory whether the password is valid and which groups the user belongs to.

That is the "single source of truth" value. The application can stay focused on application behavior while the directory remains responsible for corporate identity.

## Dependencies

The application is a Spring MVC API with Spring Security and LDAP support. The `spring-boot-starter-data-ldap` starter brings in Spring LDAP infrastructure. LDAP authentication itself lives in Spring Security's LDAP module, so the application also depends on `spring-security-ldap`.

The embedded server is UnboundID. Spring Boot manages the version, so the dependency does not need an explicit version.

```xml
{% include-markdown "../../code/36-ldap/maven/pom.xml" comments=false %}
```

Embedded LDAP is test and sample infrastructure. In production, the application would point at a real LDAP or Active Directory server instead of starting one inside the process.

## LDAP in 60 Seconds

LDAP stores entries in a hierarchy. Each entry has a distinguished name, usually shortened to DN, and the DN identifies the entry's location in the tree.

This sample uses the base DN:

```text
dc=springboot4docs,dc=dev
```

Under that base, the directory has two organizational units:

| DN | Purpose |
| --- | --- |
| `ou=people,dc=springboot4docs,dc=dev` | User entries live here. |
| `ou=groups,dc=springboot4docs,dc=dev` | Group entries live here. |

A user entry is identified by a DN such as:

```text
uid=alice,ou=people,dc=springboot4docs,dc=dev
```

The entry has attributes. In this chapter, the important attributes are `uid`, `cn`, `sn`, and `userPassword`. Real directories may have many more: email addresses, employee IDs, department names, phone numbers, manager references, and provider-specific attributes.

Groups are also entries. LDAP servers vary in how they model group membership. Some expose a `memberOf` attribute on the user. Others store membership on the group with a `member` attribute that points to user DNs. This chapter uses the second model: the `admins` group has a `member` value pointing at Alice's DN.

## Application Configuration

The application properties start an embedded LDAP server on port `8389` and load `test-server.ldif` from the classpath:

```yaml
{% include-markdown "../../code/36-ldap/maven/src/main/resources/application.yml" comments=false %}
```

The `spring.ldap.embedded.*` properties describe the embedded directory. The `base-dn` is the root of the sample directory tree. The `ldif` property tells Boot which file to import when the server starts.

The `spring.ldap.urls` property is the client-side LDAP URL. Spring LDAP components use it to connect to the directory. In this chapter the server and client are in the same process, but the configuration still looks like a normal LDAP connection.

## The LDIF

LDAP Data Interchange Format, or LDIF, is a text format for directory entries. It is convenient for tests because the whole directory state can be reviewed in source control.

```ldif
{% include-markdown "../../code/36-ldap/maven/src/main/resources/test-server.ldif" comments=false %}
```

The first entry creates the base domain component, `dc=springboot4docs,dc=dev`. The `dc` attributes come from domain-style naming. A company using `example.com` might use `dc=example,dc=com`.

The next two entries create `ou=people` and `ou=groups`. `ou` means organizational unit. In small samples, organizational units are often just buckets. In real directories, they may represent departments, locations, or other administrative boundaries.

Alice and Bob are user entries under `ou=people`. Their usernames are stored in the `uid` attribute. Their passwords are stored in `userPassword`.

The password values use LDAP's prefixed format:

```text
{SSHA}UgCJjM+VJJYduihDuk0aPpDtY9RzYWx0eQ==
```

`{SSHA}` means salted SHA-1. You will still see this format in LDAP directories. Bcrypt is also supported in many setups, but directory password formats are not universal, so check what your server actually stores and verifies.

This application uses bind authentication. Spring Security does not read the hash and compare it itself. It searches for the user's DN, then attempts to bind to LDAP as that user with the submitted password. If the LDAP server accepts the bind, the credentials are valid. If you choose password-comparison authentication instead, then Spring Security needs a `PasswordEncoder` that matches the stored LDAP password format.

The final entry creates `cn=admins` under `ou=groups`. It has one `member`: Alice's DN. That membership is what becomes `ROLE_admins` in Spring Security.

## The API

The controller has one endpoint for any authenticated user and one endpoint for admins:

```java
{% include-markdown "../../code/36-ldap/maven/src/main/java/dev/springboot4docs/ch_36_ldap/ApiController.java" comments=false %}
```

`/api/me` returns the authenticated name and authorities so the test can prove that Spring Security created an authentication from LDAP. `/api/admin/secrets` returns a plain string. The interesting part is not the controller method. The interesting part is whether the security layer allows Alice and rejects Bob.

## Security Configuration

The security configuration has two jobs: set the URL authorization policy, and create an LDAP-backed `AuthenticationManager`.

```java
{% include-markdown "../../code/36-ldap/maven/src/main/java/dev/springboot4docs/ch_36_ldap/SecurityConfig.java" comments=false %}
```

The HTTP policy is small:

```text
/api/admin/**   requires ROLE_admins
/api/**         requires authentication
everything else denied
```

HTTP Basic is enabled because it is the easiest way to demonstrate username/password authentication in an API test. A browser form login could use the same authentication manager.

The `DefaultSpringSecurityContextSource` points at the embedded LDAP server and includes the base DN:

```text
ldap://localhost:8389/dc=springboot4docs,dc=dev
```

That means the searches in the rest of the configuration are relative to `dc=springboot4docs,dc=dev`.

The `LdapBindAuthenticationManagerFactory` configures bind authentication. The user search says:

```text
base:   ou=people
filter: (uid={0})
```

When Alice sends `alice` as the username, Spring Security searches under `ou=people` for an entry whose `uid` is `alice`. The `{0}` placeholder is the submitted username. If the search finds Alice's entry, Spring Security tries to bind as:

```text
uid=alice,ou=people,dc=springboot4docs,dc=dev
```

with the submitted password.

After authentication succeeds, the authorities populator searches for groups. The group search says:

```text
base:   ou=groups
filter: member={0}
```

In this case `{0}` is the user's full DN. Alice's DN appears in the `admins` group, so Spring Security creates an authority from that group. The configuration keeps LDAP group names lowercase with `setConvertToUpperCase(false)`, so `cn=admins` becomes `ROLE_admins`.

That is why the authorization rule uses:

```java
hasAuthority("ROLE_admins")
```

Spelling the full authority is deliberate. LDAP group mapping is one of the places where teams lose time to invisible prefixes, uppercase conversion, or provider-specific group attributes.

## Tests

The tests start the full Spring Boot application context, including the embedded LDAP server, and call the API through `MockMvcTester`.

```java
{% include-markdown "../../code/36-ldap/maven/src/test/java/dev/springboot4docs/ch_36_ldap/ApplicationTests.java" comments=false %}
```

The first test proves `/api/me` is protected. No credentials means `401 Unauthorized`.

The second test sends Alice's username and password with HTTP Basic. Spring Security searches LDAP, binds as Alice, and the endpoint returns her username.

The admin tests prove authorization, not just authentication. Alice is a member of `cn=admins`, so `/api/admin/secrets` returns `200 OK`. Bob has a valid LDAP account and the same password, but he is not a member of the admins group, so the endpoint returns `403 Forbidden`.

Run the chapter from its Maven directory:

=== "Maven"
    ```bash
    ./mvnw test
    ```

=== "Gradle"
    ```bash
    ./gradlew test
    ```

That command should start the embedded LDAP server, load the LDIF, authenticate Alice and Bob through LDAP, and verify the admin group mapping.

## Production Notes

Use LDAPS in production. Plain LDAP sends traffic without TLS. Corporate directories usually expose LDAPS on port `636`, or LDAP with StartTLS, and applications should validate the server certificate.

Most real LDAP setups use a service account for searches. The service account binds first, searches for the user DN, then the authentication flow binds as the user to verify the password. Active Directory environments often have extra rules around search bases, account lockout, disabled users, and bind names.

Connection pooling matters when LDAP is on a network path. Spring LDAP can use pooling through `spring.ldap.pool.*` properties. Tune it like shared infrastructure: enough connections for expected concurrency, but not so many that every application instance overloads the directory.

Group-to-role mapping is usually the hardest part. Some directories use `member`, some use `uniqueMember`, some expose `memberOf`, and Active Directory brings its own conventions. Decide whether your application wants LDAP group names directly, a mapped set of application roles, or method-level authorization backed by a separate policy store.

Also decide how much user data belongs in the application. Authentication can come from LDAP while application-specific profile data remains in your own database. Treat the directory as the source of truth for identity, not necessarily as the best place to store every application preference.

Part V moves from security into production operations: observability, configuration, deployment, and the work needed to run Spring Boot services with confidence.
