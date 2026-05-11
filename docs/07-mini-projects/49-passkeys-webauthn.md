# Passkeys / WebAuthn

Passkeys replace shared secrets with public-key credentials. The browser and authenticator hold the private key. Your server stores the public credential record, issues short-lived challenges, and verifies that the browser response was created for your relying party and origin.

This chapter is a backend-focused mini-project. It builds the server-side shape: registration options, authentication options, origin policy, one-time challenges, credential persistence, recovery metadata, and tests. A production app should pass the browser's attestation and assertion responses into Yubico's verification APIs before saving or accepting a credential. The simplified `finish` DTOs in this sample keep the project runnable without a real browser authenticator in CI.

## Dependencies

The application uses MVC, Security, Bean Validation, Data JPA, H2, and Yubico's WebAuthn server library:

```xml
{% include-markdown "../../code/49-passkeys-webauthn/maven/pom.xml" comments=false %}
```

Spring Boot 4 uses Jackson 3 for the application JSON stack. The Yubico library's JSON helper still exposes a Jackson 2 exception type, so the sample adds `jackson-core` from the `com.fasterxml.jackson` line only for that library boundary.

## Configuration

Passkeys are bound to a relying party id and allowed origins:

```properties
{% include-markdown "../../code/49-passkeys-webauthn/maven/src/main/resources/application.properties" comments=false %}
```

For local development the relying party id is `localhost` and the allowed origin is `http://localhost:8080`. In production, this must match your real site, for example `example.com` and `https://app.example.com`. A passkey ceremony for a different origin is rejected before any credential is saved.

## Data Model

The account record stores the application username and stable WebAuthn user handle:

```java
{% include-markdown "../../code/49-passkeys-webauthn/maven/src/main/java/dev/springboot4docs/ch_49_passkeys_webauthn/PasskeyAccount.java" comments=false %}
```

Credentials are separate because one account can register multiple passkeys:

```java
{% include-markdown "../../code/49-passkeys-webauthn/maven/src/main/java/dev/springboot4docs/ch_49_passkeys_webauthn/CredentialRecord.java" comments=false %}
```

The challenge table is the replay defense:

```java
{% include-markdown "../../code/49-passkeys-webauthn/maven/src/main/java/dev/springboot4docs/ch_49_passkeys_webauthn/PasskeyChallenge.java" comments=false %}
```

Every ceremony challenge has a purpose, username, expiry time, and consumed flag. The finish step must match all of those fields. Reusing a challenge fails.

## API

The controller exposes four ceremony endpoints:

```java
{% include-markdown "../../code/49-passkeys-webauthn/maven/src/main/java/dev/springboot4docs/ch_49_passkeys_webauthn/PasskeyController.java" comments=false %}
```

The flow is:

1. `POST /api/passkeys/register/options` creates WebAuthn registration options.
2. The browser calls `navigator.credentials.create()` with those options.
3. `POST /api/passkeys/register/finish` validates and saves the new credential.
4. `POST /api/passkeys/authenticate/options` creates WebAuthn authentication options.
5. The browser calls `navigator.credentials.get()` with those options.
6. `POST /api/passkeys/authenticate/finish` validates the assertion and signs in the user.

This sample returns the WebAuthn options as a JSON string because the Yubico library already knows the exact browser-facing JSON shape.

## Ceremony Service

The service owns the important rules:

```java
{% include-markdown "../../code/49-passkeys-webauthn/maven/src/main/java/dev/springboot4docs/ch_49_passkeys_webauthn/PasskeyCeremonyService.java" comments=false %}
```

Registration creates a random challenge, builds a Yubico `PublicKeyCredentialCreationOptions`, stores the challenge, and returns browser-facing options. Authentication does the same with `PublicKeyCredentialRequestOptions`, but includes the account's allowed credential ids.

The finish methods enforce the server-side invariants that are easy to test without browser hardware:

- The origin must be allowed for this relying party.
- The challenge must exist, match the username and purpose, be unexpired, and be unused.
- A credential id cannot be registered twice.
- A credential for Alice cannot authenticate Bob.
- The authenticator sign count must move forward when the authenticator reports a non-zero counter.

In a production passkey server, the finish methods also call the Yubico verification flow with the browser's attestation or assertion response. That is the part that checks the authenticator data, client data hash, signature, relying party id hash, user presence, and user verification bits. Do not replace that with local string checks.

## Security

The sample permits the passkey endpoints because the ceremony itself is the authentication flow:

```java
{% include-markdown "../../code/49-passkeys-webauthn/maven/src/main/java/dev/springboot4docs/ch_49_passkeys_webauthn/SecurityConfig.java" comments=false %}
```

The controller still rejects everything outside `/api/passkeys/**`. A real app would create a session or issue an application token after a successful authentication finish.

Errors use `ProblemDetail`:

```java
{% include-markdown "../../code/49-passkeys-webauthn/maven/src/main/java/dev/springboot4docs/ch_49_passkeys_webauthn/PasskeyExceptionHandler.java" comments=false %}
```

## Tests

The integration test runs through registration and authentication with HTTP requests:

```java
{% include-markdown "../../code/49-passkeys-webauthn/maven/src/test/java/dev/springboot4docs/ch_49_passkeys_webauthn/PasskeyCeremonyIntegrationTest.java" comments=false %}
```

The tests prove the server behavior that must be stable before adding a browser UI:

- Registration saves a credential and consumes the challenge.
- A disallowed origin is rejected.
- Authentication requires a known credential and increasing sign count.
- One user's credential cannot sign in another user.

Run it:

```bash
./mvnw -q -B test
```

## Recovery

Passkeys need account recovery design. Users lose devices, replace phones, and leave companies. The registration result includes recovery method names so the sample keeps that concern visible:

```json
{
  "username": "alice",
  "credentialId": "YWxpY2UtY3JlZGVudGlhbA",
  "recoveryMethods": ["recovery-code", "verified-email"]
}
```

For a real system, recovery is policy work, not just code. Common approaches include one-time recovery codes, verified email with risk checks, help-desk proofing for enterprise accounts, and allowing multiple passkeys per account so a second device can recover the first.

## What To Carry Forward

Passkeys are secure because the browser, authenticator, and server all check different parts of the same ceremony. The server-side responsibilities are concrete: exact relying party configuration, origin checks, unpredictable challenges, one-time challenge use, credential storage, signature verification through a library, and a recovery story.

