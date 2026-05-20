# CSRF, Headers, CORS-Security Interplay

Spring Security protects a browser application in more than one way at the same time. Authentication decides who the user is. Authorization decides what that user may do. CSRF protection asks a different question: did this state-changing browser request really come from a page in this application? Response headers add another layer by telling the browser not to sniff content, frame the page, cache sensitive responses, or downgrade HTTPS habits.

This chapter keeps the domain tiny so the moving pieces stay visible. The sample has one controller and three security configurations. Only one configuration is active at a time, selected by Spring profile:

- `default`: classic browser app, form login, sessions, default CSRF, default headers.
- `api`: API-only, HTTP Basic, stateless sessions, CSRF disabled for a header-authenticated API.
- `spa`: browser app for an SPA, CSRF on, token exposed through a readable `XSRF-TOKEN` cookie.

## Dependencies

The sample uses Spring MVC, Spring Security, and the matching MVC/security test starters:

```xml
{% include-markdown "../../code/31-csrf-headers/maven/pom.xml" comments=false %}
```

There is no persistence layer. The controller returns fixed records because the chapter is about the HTTP security boundary.

## The Controller

The controller exposes two widget routes and one CSRF helper route:

```java
{% include-markdown "../../code/31-csrf-headers/maven/src/main/java/dev/springboot4docs/ch_31_csrf_headers/WidgetController.java" comments=false %}
```

`GET /widgets` is a safe request. It reads state and should not need a CSRF token. `POST /widgets` changes state, so it is exactly the kind of request CSRF protection cares about.

`GET /csrf` returns the `CsrfToken` from the request. In Spring Security, the token is available as a request attribute, and MVC can inject it as a controller parameter. Server-rendered HTML usually puts this value in a hidden form field. An SPA often calls a small endpoint like this, or relies on a CSRF cookie, then sends the token back in a header on writes.

## Security Headers

Spring Security's default headers are deliberately boring. That is a compliment. They cover old but still important browser attack classes without making each controller remember to set them:

- `X-Content-Type-Options: nosniff` tells browsers not to guess a different MIME type. That reduces attacks where an uploaded or mislabeled file is interpreted as executable script.
- `X-Frame-Options: DENY` tells browsers not to render the response in a frame. That defends against clickjacking, where an attacker overlays invisible or misleading UI on top of your page.
- `Strict-Transport-Security` is sent for HTTPS requests and tells browsers to keep using HTTPS for the site. It is not meaningful on plain HTTP responses.
- `Cache-Control: no-cache, no-store, max-age=0, must-revalidate`, plus related cache headers, reduce the chance that sensitive authenticated pages are stored and replayed from browser or proxy caches.

Older Spring Security versions also wrote `X-XSS-Protection`. Modern Spring Security does not enable it by default because modern browsers either ignore it or removed the old reflected-XSS filter. Treat output encoding and Content Security Policy as the serious defenses for script injection; do not rely on `X-XSS-Protection`.

You can customize the header writer chain, but leave the defaults on unless you have a specific, tested reason. Most applications need stricter headers over time, not fewer headers.

## CSRF in 60 Seconds

CSRF stands for cross-site request forgery. The classic attack is simple: a victim is logged in to your site with a session cookie. They visit an attacker-controlled page. That page submits a form or triggers a request to your application. The browser automatically includes your application's cookies, because cookies are ambient credentials. Without another signal, the server cannot tell whether the request was created by your page or by an attacker page.

CSRF protection adds that missing signal. The server creates a per-session token and expects it on non-safe requests such as `POST`, `PUT`, `PATCH`, and `DELETE`. A legitimate page can include the token in a hidden form input or request header. An attacker site can cause the browser to send cookies, but it should not be able to read the victim's token from your origin.

Spring Security has three common repository choices:

- `HttpSessionCsrfTokenRepository` is the default. The token lives in the HTTP session. Server-rendered forms usually send it as a hidden `_csrf` field.
- `CookieCsrfTokenRepository.withHttpOnlyFalse()` stores the token in an `XSRF-TOKEN` cookie that JavaScript can read. The SPA copies that value into the `X-XSRF-TOKEN` header on writes. `HttpOnly` must be false for JavaScript to read it, so use this pattern only for the CSRF token, not for session cookies.
- A custom repository is possible, but rare. Reach for it only when an application has a real cross-service or legacy-token requirement.

Do not disable CSRF because a test got a `403`. That usually means the test is correctly exercising a protected write without sending a token. Fix the test by adding a CSRF token. Disable CSRF only when the authentication model removes the browser-cookie CSRF surface, such as a stateless API that authenticates every request with an `Authorization: Bearer ...` header and does not rely on cookies.

SameSite cookies reduce CSRF risk, especially with modern `Lax` behavior, but they are not a replacement for CSRF tokens. SameSite has browser compatibility details, redirect edge cases, and deliberate opt-outs for some flows. Tokens remain the explicit application-level proof.

## Classic Browser App

The default profile is the form-login, session-based browser configuration:

```java
{% include-markdown "../../code/31-csrf-headers/maven/src/main/java/dev/springboot4docs/ch_31_csrf_headers/BrowserAppSecurityConfig.java" comments=false %}
```

There is no explicit `.csrf(...)` call and no explicit `.headers(...)` call. That is the point: CSRF protection and the default response headers are on. Because this is a form-login application, requests are authenticated with a session cookie after login. That is the environment where CSRF protection matters most.

The sample user is shared by the three configurations:

```java
{% include-markdown "../../code/31-csrf-headers/maven/src/main/java/dev/springboot4docs/ch_31_csrf_headers/SecurityUsers.java" comments=false %}
```

Run the app with the default profile by not selecting any profile:

=== "Maven"
    ```bash
    ./mvnw spring-boot:run
    ```

=== "Gradle"
    ```bash
    ./gradlew bootRun
    ```

In a real server-rendered form, the view template would include the CSRF parameter name and token as a hidden input. Spring MVC view technologies and Spring Security integrations can do that for you, but the underlying rule is the same as the test: a protected write needs the token.

## API-Only App

The `api` profile makes the opposite trade-off:

```java
{% include-markdown "../../code/31-csrf-headers/maven/src/main/java/dev/springboot4docs/ch_31_csrf_headers/ApiOnlySecurityConfig.java" comments=false %}
```

This configuration disables CSRF and sets stateless session creation. The comment in the code is intentional. Disabling CSRF should be justified where the decision is made.

The reasoning is not "APIs do not need CSRF." Some APIs do need it. The reasoning is narrower: a stateless API that authenticates each request with a header token does not depend on ambient browser cookies. A forged form post from another origin cannot invent the victim's `Authorization` header. In this small sample the credential is HTTP Basic because it is easy to test, but the same design point usually appears with bearer JWTs or opaque access tokens.

Start this profile explicitly:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=api
```

If you later add cookie-based login to this API, revisit the CSRF decision. Security configuration follows the credential model.

## SPA Browser App

The `spa` profile keeps CSRF on, but changes where the token is stored:

```java
{% include-markdown "../../code/31-csrf-headers/maven/src/main/java/dev/springboot4docs/ch_31_csrf_headers/SpaSecurityConfig.java" comments=false %}
```

`CookieCsrfTokenRepository.withHttpOnlyFalse()` writes the token to a cookie named `XSRF-TOKEN`. The SPA reads that cookie and echoes the value in the `X-XSRF-TOKEN` header on `POST`, `PUT`, `PATCH`, and `DELETE` requests.

This is not the same as using the CSRF cookie as an authentication cookie. The token is not secret in the same way a session id is secret. It is a same-origin proof that JavaScript running in your application could read the token and attach it to the write request. Keep the session cookie `HttpOnly`; make only the CSRF token cookie readable when your SPA needs to read it.

Start the SPA-flavored configuration with:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=spa
```

## CORS and CSRF

CORS and CSRF solve different problems. CORS is a browser policy that decides whether JavaScript from one origin may read responses from another origin. CSRF is a server-side check for state-changing requests that arrive with browser credentials.

Spring Security understands CORS preflight requests. An `OPTIONS` preflight is not treated like a forged state-changing request that needs a CSRF token. That would break normal cross-origin negotiation before the browser has even sent the real request.

The actual request is different. If CSRF is enabled and the browser sends credentials, the real `POST` still needs a valid CSRF token. Allowing an origin with CORS does not mean "skip CSRF." It only means the browser may make the cross-origin call and expose the response when the CORS rules match.

That distinction matters for SPAs. If your SPA is served from `https://app.example` and calls `https://api.example` with credentials, configure CORS for the SPA origin, send cookies deliberately, and still include the CSRF token on unsafe methods.

## Tests

The browser-app tests activate the `default` profile:

```java
{% include-markdown "../../code/31-csrf-headers/maven/src/test/java/dev/springboot4docs/ch_31_csrf_headers/BrowserAppSecurityWebMvcTest.java" comments=false %}
```

Both tests authenticate as `alice`, so authentication is not the variable. The first `POST /widgets` omits the CSRF token and receives `403`. The second adds `.with(csrf())` from Spring Security's test support and receives `200`.

This is the "Cannot POST in tests" footgun. A CSRF-protected `POST` without `.with(csrf())` is supposed to fail. The error is telling you that the request is missing the same token your browser form or SPA would have sent. Fix the test request; do not disable CSRF to make the red bar disappear.

The API profile proves the intentional exception:

```java
{% include-markdown "../../code/31-csrf-headers/maven/src/test/java/dev/springboot4docs/ch_31_csrf_headers/ApiOnlySecurityWebMvcTest.java" comments=false %}
```

The request is authenticated with HTTP Basic but sends no CSRF token. It succeeds because the `api` profile disabled CSRF for a stateless, header-authenticated API.

The SPA profile exercises the cookie-token pattern:

```java
{% include-markdown "../../code/31-csrf-headers/maven/src/test/java/dev/springboot4docs/ch_31_csrf_headers/SpaSecurityWebMvcTest.java" comments=false %}
```

The test calls `GET /csrf`, reads the returned token, keeps the `XSRF-TOKEN` cookie, and sends both the cookie and the `X-XSRF-TOKEN` header on `POST /widgets`. That write succeeds. A second write without the token receives `403`.

Run the chapter tests from the code directory:

=== "Maven"
    ```bash
    ./mvnw test
    ```

=== "Gradle"
    ```bash
    ./gradlew test
    ```

The assertions use `.hasStatus(403)` for forbidden responses. `MockMvcTester` has convenient helpers such as `.hasStatusOk()`, but not a `.hasStatusForbidden()` shortcut.

## What to Remember

Leave Spring Security's default headers on. They are low-effort browser hardening for content sniffing, clickjacking, HTTPS downgrade habits, and sensitive response caching.

Leave CSRF on for browser apps that use cookies. Use the default session token repository for server-rendered pages, and use the cookie token repository when an SPA needs to read a token and echo it in a header.

Disable CSRF only when the credential model supports that decision. Stateless APIs using header authentication are the common case. Cookie-authenticated APIs and browser apps are not.

Chapter 32 moves from session and basic authentication to JWT resource servers, where the "header token on every request" model becomes the main path.
