# Form Login And Sessions

HTTP Basic is useful for APIs and scripts, but it is not the browser login flow most users expect. A browser application usually shows an HTML login form, accepts a username and password with `POST /login`, creates an HTTP session after successful authentication, and sends the browser to the protected part of the site.

That is Spring Security form login. The filter chain still does the security work before MVC reaches your controller, but the protocol is different from Basic authentication. The normal flow is:

```text
GET /login
  -> render the login page

POST /login
  -> UsernamePasswordAuthenticationFilter reads username, password, and CSRF token
  -> AuthenticationManager checks the credentials
  -> SecurityContext is stored in the HTTP session
  -> 302 redirect to /dashboard

failed POST /login
  -> 302 redirect to /login?error
```

When authentication succeeds, the servlet container gives the browser a `JSESSIONID` cookie for the session. Spring Security stores the authenticated `SecurityContext` in that session under the key `SPRING_SECURITY_CONTEXT`. On later requests, the session id cookie lets Spring Security find that context again and restore the authenticated principal for the request.

Logout reverses that state. The default `/logout` endpoint clears the security context and invalidates the session. In modern Spring Security, logout is a `POST` by default when CSRF protection is enabled. Do not build GET logout links. Use a form with the CSRF token, just like the login form.

## Dependencies

This chapter project includes Spring MVC, Spring Security, Thymeleaf, and the MVC/security test starters:

```xml
{% include-markdown "../../code/28-form-login/maven/pom.xml" comments=false %}
```

Adding Thymeleaf gives us server-rendered HTML views. Adding Spring Security gives us the form-login filter, CSRF protection, session integration, and the `/logout` endpoint.

## The Default Login Page

Before writing a custom template, it helps to know what Spring Security can do by itself. If you enable form login with defaults:

```java
http.formLogin(Customizer.withDefaults());
```

Spring Security generates a simple login page at `GET /login`. That generated page posts back to `POST /login` with fields named `username` and `password`, and it includes a CSRF token. It is good for first startup and quick demos. It is not where real applications usually stop, because you normally need your own layout, messages, links, and styling.

The custom page in this chapter keeps the same HTTP contract. Only the HTML changes.

That contract is the part to protect in tests. A pretty login page can still be broken if it posts to the wrong URL, uses different field names, or forgets the CSRF token. Spring Security's defaults are intentionally conventional: `POST /login`, `username`, `password`, success redirect, failure redirect. Staying close to those defaults keeps the application easier to reason about while still letting you own the HTML.

## Security Configuration

Here is the complete security configuration:

```java
{% include-markdown "../../code/28-form-login/maven/src/main/java/dev/springboot4docs/ch_28_form_login/SecurityConfig.java" comments=false %}
```

The authorization rules are deliberately small:

```java
.requestMatchers("/", "/login", "/css/**").permitAll()
.anyRequest().authenticated()
```

Anonymous users can load the home page, the login page, and static CSS. Everything else requires authentication. That means `/dashboard` is protected. If an anonymous browser asks for `/dashboard`, Spring Security saves the original request and redirects the browser to `/login`.

Saved requests are useful in real applications. If a user opens `/orders/123` while signed out, the application can send them through login and then return them to `/orders/123`. This chapter chooses `defaultSuccessUrl("/dashboard", true)` so the example always lands on the same page after login. Once the mechanics are familiar, you can remove the `true` flag when returning to the originally requested page is a better user experience.

The form-login block defines the browser authentication flow:

```java
.formLogin((form) -> form
		.loginPage("/login")
		.defaultSuccessUrl("/dashboard", true)
		.permitAll())
```

`loginPage("/login")` tells Spring Security that our MVC controller and Thymeleaf template will render the login page. `defaultSuccessUrl("/dashboard", true)` sends every successful login to `/dashboard`. The `true` matters: without it, Spring Security may redirect to a previously saved protected request instead. That behavior is often useful in applications, but this chapter keeps the flow predictable.

The user store is an `InMemoryUserDetailsManager` with one demo account:

```text
username: alice
password: secret
role: USER
```

The password is still encoded with `PasswordEncoderFactories.createDelegatingPasswordEncoder()`. The raw string exists only in this demo configuration. Stored passwords should be encoded, even when the user store is temporary.

The logout line keeps the default logout endpoint and makes it available to authenticated users:

```java
.logout((logout) -> logout.permitAll())
```

With CSRF protection on, the default logout request is `POST /logout`. A successful logout redirects to `/login?logout`.

## MVC Controller

The controller only selects views:

```java
{% include-markdown "../../code/28-form-login/maven/src/main/java/dev/springboot4docs/ch_28_form_login/WebController.java" comments=false %}
```

`GET /` returns the public home view for anonymous users. If the current request is already authenticated, it redirects to `/dashboard`.

`GET /login` returns the login view. The controller does not handle `POST /login`; that request is owned by Spring Security's `UsernamePasswordAuthenticationFilter`.

That split is important. If you add a controller method for `POST /login`, you are probably fighting the framework instead of configuring it. Let Spring Security own the credential submission endpoint, and customize the surrounding behavior through the security DSL, templates, user store, success handlers, or failure handlers.

`GET /dashboard` adds the current principal name to the model. The security chain has already required authentication before this method runs, so the controller can treat the `Principal` as the signed-in user.

## Templates

The home page is only a public entry point:

```html
{% include-markdown "../../code/28-form-login/maven/src/main/resources/templates/home.html" comments=false %}
```

The login form is the important template:

```html
{% include-markdown "../../code/28-form-login/maven/src/main/resources/templates/login.html" comments=false %}
```

The form posts to `/login` and uses the field names Spring Security expects by default:

```html
<input name="username" type="text">
<input name="password" type="password">
```

The hidden CSRF field is required:

```html
<input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}">
```

Thymeleaf reads the request's CSRF object and renders the real parameter name and token value. If that field is missing, Spring Security rejects the `POST /login` request with `403 Forbidden` before credentials are checked. That failure can look confusing because the username and password may be correct. The request is rejected because the browser submitted a state-changing form without the CSRF token.

The dashboard displays the authenticated user and posts logout with another CSRF token:

```html
{% include-markdown "../../code/28-form-login/maven/src/main/resources/templates/dashboard.html" comments=false %}
```

A logout button should be a form, not a link, because the default logout endpoint expects `POST /logout`. The CSRF token makes the sign-out action intentional from this page.

The CSS file is tiny on purpose:

```css
{% include-markdown "../../code/28-form-login/maven/src/main/resources/static/css/site.css" comments=false %}
```

The point is simply to prove that `/css/**` is public. The login page can load its stylesheet before the user signs in.

## Session Settings

The sample also sets a session timeout:

```yaml
{% include-markdown "../../code/28-form-login/maven/src/main/resources/application.yml" comments=false %}
```

`server.servlet.session.timeout: 30m` tells the servlet container to expire idle sessions after 30 minutes. Authentication does not last forever just because the browser has a session cookie.

Spring Security also applies session fixation protection by default. On successful login, a new session id is created so an attacker cannot force a victim to keep using a known pre-login session id. The application code does not need to call this directly; it is part of the default form-login/session behavior.

The session is server-side state. The browser only receives an opaque id in the `JSESSIONID` cookie; it does not receive the `SecurityContext` itself. That distinction matters when debugging. If a browser has an old cookie but the server-side session expired, the next protected request is anonymous again and will be redirected to login.

For applications where one account should only be signed in from one browser at a time, Spring Security can limit concurrent sessions:

```java
http.sessionManagement((sessions) -> sessions
		.maximumSessions(1));
```

That belongs in the security chain when the product requirement is real. Do not add it only because sessions exist.

## Tests

The tests use the Spring Boot Web MVC slice and import the real security configuration:

```java
{% include-markdown "../../code/28-form-login/maven/src/test/java/dev/springboot4docs/ch_28_form_login/FormLoginWebMvcTest.java" comments=false %}
```

`GET /login` proves that anonymous users can render the custom login page and that MVC selects the `login` view. `GET /dashboard` as an anonymous user proves the protected page redirects to `/login`.

The login test uses `formLogin("/login")` from `SecurityMockMvcRequestBuilders`. This is a request builder, not a request post-processor. It builds the same kind of `POST /login` request the browser would send, including the CSRF token:

```java
this.mvc.perform(formLogin("/login")
		.user("alice")
		.password("secret"))
```

The test then checks the redirect to `/dashboard`, verifies that `alice` authenticated, and verifies that the request now has a session containing `SPRING_SECURITY_CONTEXT`.

`@WithMockUser("alice")` is used for the dashboard rendering test because that test is not about the credential flow. It says, "given an authenticated user named alice, does the page render the principal name?"

This is the same testing split used in the previous chapter. Use the real form-login request when the login mechanism matters. Use `@WithMockUser` when the test starts after authentication and only needs a principal with a name or roles. If every test uses `@WithMockUser`, the application can accidentally break `POST /login` without the suite noticing.

The logout test uses `formLogout()`, which builds a CSRF-protected `POST /logout`. After logout, `/dashboard` is protected again for an anonymous request and redirects back to `/login`.

Run the tests from the chapter project:

=== "Maven"
    ```bash
    ./mvnw test
    ```

=== "Gradle"
    ```bash
    ./gradlew test
    ```

## Run It

Start the application:

=== "Maven"
    ```bash
    ./mvnw spring-boot:run
    ```

=== "Gradle"
    ```bash
    ./gradlew bootRun
    ```

Open `http://localhost:8080/` in a browser. The home page is public. Follow the dashboard link and Spring Security redirects you to `/login`.

Sign in as `alice` with password `secret`. The login form posts to `/login`, Spring Security authenticates the credentials, creates the session, stores the security context, and redirects to `/dashboard`. The dashboard shows `alice`.

Press the sign-out button. The browser sends `POST /logout` with a CSRF token, Spring Security clears the security context and session, and the browser lands on `/login?logout`. Trying `/dashboard` again sends you back to the login page.

That is the standard browser form-login shape: public login page, protected pages, CSRF-protected forms, authenticated session, and POST logout. Chapter 29 keeps the same security chain shape but moves users and encoded passwords out of code and into JDBC-backed storage.
