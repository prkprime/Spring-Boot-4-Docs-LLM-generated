# OAuth2 Login

The previous two chapters treated OAuth2 from the API side. A request arrived with a bearer token, Spring Security validated that token, and the controller received an authenticated principal. That is the resource-server shape.

This chapter moves to browser login: "Sign in with Google", "Sign in with GitHub", or another identity provider. The application does not collect a password. It sends the user to the provider, receives an authorization code, exchanges that code for tokens, and creates a Spring Security authentication for the browser session.

That is usually the right default for application login. Identity is hard. Password storage, account recovery, email verification, bot defense, MFA, breach monitoring, suspicious-login detection, and consent screens are not side quests you want every small app to build alone. If the user can sign in through a provider you trust, let the provider own the most sensitive part.

## Dependencies

The sample uses Spring MVC, Thymeleaf, Spring Security, and the OAuth2 client starter:

```xml
{% include-markdown "../../code/34-oauth2-login/maven/pom.xml" comments=false %}
```

The important difference from the resource-server chapters is the client starter. A resource server validates tokens presented to it. An OAuth2 login application is an OAuth2 client: it starts the authorization request, handles the redirect callback, exchanges the authorization code, loads user information, and stores the resulting authentication in the session.

## Client Registration

Spring Boot reads OAuth2 client registrations from configuration:

```yaml
{% include-markdown "../../code/34-oauth2-login/maven/src/main/resources/application.yml" comments=false %}
```

The shape is:

```text
spring.security.oauth2.client.registration.<registration-id>.client-id
spring.security.oauth2.client.registration.<registration-id>.client-secret
spring.security.oauth2.client.registration.<registration-id>.scope
```

The `<registration-id>` becomes part of the local login URL. In this sample, `/oauth2/authorization/github` starts the GitHub login flow and `/oauth2/authorization/google` starts the Google login flow.

GitHub and Google are built-in providers, so the sample only needs the client id and client secret. Spring Boot and Spring Security already know the common provider metadata: authorization endpoint, token endpoint, user-info endpoint, user-name attribute, and issuer details where appropriate. Facebook and Okta also have shipped defaults.

The redirect URI you register with the provider normally follows Spring Security's default callback path:

```text
{baseUrl}/login/oauth2/code/{registrationId}
```

For local development that might become `http://localhost:8080/login/oauth2/code/google`. In production it should be the HTTPS URL for the deployed application. The registration id must match the value in configuration; a Google client registered as `google` comes back to `/login/oauth2/code/google`.

For a provider that is not built in, add a matching `provider` block:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          example:
            client-id: ${EXAMPLE_CLIENT_ID}
            client-secret: ${EXAMPLE_CLIENT_SECRET}
            scope: openid,profile,email
        provider:
          example:
            issuer-uri: https://idp.example.com
```

Use placeholders for real secrets. The fallback `demo-client-id` and `demo-client-secret` values in this chapter let the application context start locally and let tests run, but they are not valid provider credentials.

## The Login Flow

OAuth2 Login uses the Authorization Code flow. For OpenID Connect providers, such as Google, the response also includes an ID token and Spring Security creates an `OidcUser`.

The browser round-trip looks like this:

```text
Browser                  Spring Boot app                  Google/GitHub
   |                            |                              |
   | GET /                      |                              |
   |<-- home page with links ---|                              |
   |                            |                              |
   | GET /oauth2/authorization/google                         |
   |--------------------------->|                              |
   |<-- 302 to provider --------|                              |
   |                            |                              |
   | GET provider authorization endpoint                       |
   |---------------------------------------------------------->|
   |                            |                              |
   | user signs in / consents   |                              |
   |                            |                              |
   | GET /login/oauth2/code/google?code=...&state=...          |
   |<----------------------------------------------------------|
   |--------------------------->|                              |
   |                            | POST token request           |
   |                            |----------------------------->|
   |                            |<-- tokens -------------------|
   |                            |                              |
   |                            | creates Authentication       |
   |                            | stores it in SecurityContext |
   |<-- authenticated session --|                              |
```

In more concrete Spring Security terms:

1. The user clicks `Sign in with Google`.
2. The browser requests `/oauth2/authorization/google`.
3. Spring Security builds an authorization request and redirects to Google.
4. The user signs in at Google.
5. Google redirects back to `/login/oauth2/code/google?code=...`.
6. Spring Security validates the `state`, exchanges the code for tokens, loads the user, and creates an authenticated principal.
7. The authentication is stored in the `SecurityContext` and the HTTP session.

For public clients, keep PKCE enabled. PKCE binds the authorization code to the client that started the flow, reducing the value of a stolen code. Do not turn it off to make a provider setup look simpler.

## Security Configuration

The application security configuration is intentionally small:

```java
{% include-markdown "../../code/34-oauth2-login/maven/src/main/java/dev/springboot4docs/ch_34_oauth2_login/SecurityConfig.java" comments=false %}
```

The authorization rules allow the home page and local login page anonymously:

```java
.authorizeHttpRequests((authorize) -> authorize
		.requestMatchers("/", "/login/**").permitAll()
		.anyRequest().authenticated())
```

Every other endpoint needs an authenticated user. That includes `/me`, which returns the current principal as JSON.

The OAuth2 switch is this line:

```java
.oauth2Login(Customizer.withDefaults())
```

That installs the filters that start the authorization request, handle the redirect callback, exchange the authorization code, and create the final authentication. The application does not implement a controller for `/oauth2/authorization/{registrationId}` or `/login/oauth2/code/{registrationId}`. Those endpoints are handled by Spring Security.

The sample also configures `/me` to return `401 Unauthorized` for anonymous requests. Browser navigation can use redirects, but a JSON endpoint is easier to test and consume when it gives an HTTP status instead of an HTML login page.

Logout clears the local session:

```java
.logout((logout) -> logout
		.logoutSuccessUrl("/")
		.invalidateHttpSession(true)
		.clearAuthentication(true)
		.deleteCookies("JSESSIONID"))
```

That signs the user out of this application. It does not necessarily sign the user out of Google, GitHub, or another provider. If the user clicks "Sign in with Google" again, the provider may still have its own session and may redirect back without asking for a password.

For OpenID Connect providers that support RP-initiated logout, wire an `OidcClientInitiatedLogoutSuccessHandler`:

```java
@Bean
LogoutSuccessHandler oidcLogoutSuccessHandler(ClientRegistrationRepository registrations) {
	OidcClientInitiatedLogoutSuccessHandler handler =
			new OidcClientInitiatedLogoutSuccessHandler(registrations);
	handler.setPostLogoutRedirectUri("{baseUrl}/");
	return handler;
}
```

Then use it from `logout().logoutSuccessHandler(...)`. Provider support varies, so treat full single-sign-out as provider-specific behavior, not a guarantee of OAuth2 login in general.

## Controller

The controller renders the page and exposes the authenticated principal:

```java
{% include-markdown "../../code/34-oauth2-login/maven/src/main/java/dev/springboot4docs/ch_34_oauth2_login/WebController.java" comments=false %}
```

The home endpoint accepts an optional `OAuth2User`:

```java
String home(@AuthenticationPrincipal OAuth2User user, Model model)
```

Anonymous users get `null`. Authenticated users get the principal created by Spring Security after the provider callback.

The `/me` endpoint shows the two principal shapes you usually care about:

```java
if (user instanceof OidcUser oidcUser) {
	// OpenID Connect: id_token claims are available
}
```

`OAuth2User` is the general OAuth2 user type. It exposes a `name` and an attribute map. GitHub login normally lands here because GitHub is an OAuth2 provider but not an OpenID Connect provider for this flow.

`OidcUser` is the OpenID Connect-specific type. It extends `OAuth2User` and adds OIDC concepts such as the subject, ID token, and standard claims like `email`, `email_verified`, `given_name`, and `family_name`. Google login normally lands here because the configured scope includes `openid`.

Do not assume every provider returns the same claim names. `email` is common, but not universal. GitHub may require email-specific API calls depending on account visibility. Google gives you OIDC claims when the `openid profile email` scopes are present.

Treat the provider subject as the stable external identity. In OIDC that is the `sub` claim. In plain OAuth2 it is whatever attribute the provider declares as the user-name attribute. Display names and email addresses are useful profile data, but they are not as stable as the provider's subject identifier.

## Templates

The home page is just a Thymeleaf view:

```html
{% include-markdown "../../code/34-oauth2-login/maven/src/main/resources/templates/home.html" comments=false %}
```

The important links are plain anchors:

```html
<a href="/oauth2/authorization/github">Sign in with GitHub</a>
<a href="/oauth2/authorization/google">Sign in with Google</a>
```

You do not post credentials to your app. The link starts the OAuth2 authorization request, and Spring Security redirects the browser to the provider.

The local login page uses the same provider entry points:

```html
{% include-markdown "../../code/34-oauth2-login/maven/src/main/resources/templates/login.html" comments=false %}
```

When the user is authenticated, the home page shows the principal name and a logout form. Logout remains a POST because CSRF protection is still enabled. That is what you want for browser session applications.

## Custom User Mapping

Many applications need a local user record even when authentication is outsourced. For example, your database might have an `AppUser` with billing status, tenant membership, feature flags, or an internal role model. Do not force the whole application to depend directly on provider-specific claims.

The usual hook is an `OAuth2UserService`. It lets Spring Security fetch the provider user, then lets you map or wrap that user before the authentication is stored:

```java
@Bean
OAuth2UserService<OAuth2UserRequest, OAuth2User> oauth2UserService(AppUserRepository users) {
	DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
	return (request) -> {
		OAuth2User oauth2User = delegate.loadUser(request);
		String registrationId = request.getClientRegistration().getRegistrationId();
		String externalId = oauth2User.getName();
		AppUser appUser = users.findOrCreate(registrationId, externalId, oauth2User.getAttributes());
		return new AppPrincipal(appUser, oauth2User);
	};
}
```

For OpenID Connect, use `OidcUserService` in the same role. Keep this mapping boring and explicit: registration id plus provider subject is usually a better stable key than email, because users can change email addresses.

After that mapping, application code should usually depend on your own principal or user id, not on provider JSON. That keeps the rest of the system insulated from provider differences and lets you add a second provider later without rewriting authorization checks.

## Tests

The tests use the Spring Boot 4 MVC slice, import the real security configuration, and drive requests through `MockMvcTester`:

```java
{% include-markdown "../../code/34-oauth2-login/maven/src/test/java/dev/springboot4docs/ch_34_oauth2_login/OAuth2LoginWebMvcTest.java" comments=false %}
```

The anonymous home-page test proves that `/` is public and that the provider links render. It does not contact GitHub or Google. It only verifies the local page.

That distinction matters. A provider integration test that drives a real browser through Google would be slow, brittle, and hard to run in CI. The contract you own is your Spring configuration, your links, your controller behavior, and your user mapping. Spring Security's test support lets you exercise those pieces deterministically.

The anonymous `/me` test expects `401`:

```java
.assertThat()
.hasStatus(401);
```

Use numeric status assertions with `MockMvcTester`. Do not reach for nonexistent helpers such as `.hasStatusUnauthorized()`.

The OAuth2 user test uses this request post-processor:

```java
oauth2Login().oauth2User(user)
```

That installs an OAuth2 authentication directly into the mock request. There is no real provider, no browser redirect, no authorization code, and no token exchange. The point of this test is not to retest Google. It is to prove that your controller behaves correctly once Spring Security has authenticated an OAuth2 user.

The OIDC test uses the OIDC-specific helper:

```java
oidcLogin().idToken((idToken) -> idToken
		.claim("name", "Alice Google")
		.claim("email", "alice@example.com"))
```

That creates an `OidcUser`, including ID token claims, so the test can verify that `/me` exposes those claims. This is the correct level for MVC tests: fake the authenticated principal, then assert your application behavior.

Run the chapter tests from the code directory:

=== "Maven"
    ```bash
    ./mvnw test
    ```

=== "Gradle"
    ```bash
    ./gradlew test
    ```

## Production Notes

Most providers require HTTPS redirect URIs outside localhost. Register the exact callback URL with the provider, usually:

```text
https://your-app.example.com/login/oauth2/code/{registrationId}
```

Keep provider secrets outside source control. Environment variables, platform secrets, or a dedicated secret manager are normal choices.

Do not store ID tokens or access tokens in cookies sent to the browser. In this server-side MVC application, Spring Security keeps the authentication in the server-side session. If you need to call provider APIs later, use Spring Security's authorized-client support instead of inventing a token cookie.

Keep PKCE enabled for public clients. Use narrow scopes. Ask for `openid profile email` only when you actually need those claims.

Chapter 35 switches back to the API side and validates JWTs from a real provider-shaped environment with Keycloak and Testcontainers.
