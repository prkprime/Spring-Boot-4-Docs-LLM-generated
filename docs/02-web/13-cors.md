# CORS

CORS is the browser security model that decides whether JavaScript loaded from one origin may read a response from another origin. It is not a general server-to-server security feature. `curl`, a backend service, and a mobile app can call your API without CORS. The browser is the actor that enforces it.

An origin is the tuple of scheme, host, and port. These are all different origins:

```text
https://app.example.com
http://app.example.com
https://app.example.com:8443
https://api.example.com
```

The browser's same-origin policy says that a page from `https://app.example.com` cannot freely read responses from `https://api.example.com`. CORS is the protocol that lets the API opt in. The API does that by returning response headers such as `Access-Control-Allow-Origin`.

For a simple cross-origin `GET`, the browser may send the real request with an `Origin` header and then inspect the response:

```text
GET /widgets/all
Origin: https://app.example.com

HTTP/1.1 200
Access-Control-Allow-Origin: https://app.example.com
```

For requests that need a preflight, the browser first asks permission with `OPTIONS`:

```text
browser                         API
   |                             |
   | OPTIONS /widgets/all        |
   | Origin: https://app...      |
   | Access-Control-Request-Method: GET
   |---------------------------->|
   |                             |
   | 200                         |
   | Access-Control-Allow-Origin: https://app...
   | Access-Control-Allow-Methods: GET,POST,OPTIONS
   |<----------------------------|
   |                             |
   | GET /widgets/all            |
   | Origin: https://app...      |
   |---------------------------->|
   |                             |
   | 200 + response body         |
   | Access-Control-Allow-Origin: https://app...
   |<----------------------------|
```

A preflight is common when the request uses a non-simple method, sends non-simple headers such as `Authorization` or many custom headers, or uses a content type outside the browser's simple request rules. The important point for Spring MVC work is practical: the browser sends `OPTIONS`, not your JavaScript application code, and the browser expects the CORS response headers on the preflight response before it sends the real request.

## Three Configuration Points

Spring MVC gives you three common places to configure CORS.

The fastest and most local option is `@CrossOrigin` on a controller method or controller class. Use this when one endpoint has a special rule and the rule belongs directly beside that handler:

```java
@CrossOrigin(origins = "https://localhost:3000")
@GetMapping("/widgets/local")
List<Widget> local() {
	return this.widgets;
}
```

That is useful for a one-off local development endpoint or a controller whose CORS rule is genuinely different from the rest of the application. It is not a great default for a large API because the policy gets scattered across handlers.

The normal application-level option is `WebMvcConfigurer.addCorsMappings(...)`. This configures CORS rules per MVC path pattern:

```java
{% include-markdown "../../code/13-cors/maven/src/main/java/dev/springboot4docs/ch_13_cors/WebConfig.java" comments=false %}
```

This is the default choice for most Spring MVC applications. It keeps browser access policy in one place, it can still be scoped by path, and it participates in MVC handler mapping.

The outermost option is a servlet `CorsFilter` bean. It runs in the servlet filter chain, before Spring MVC selects a controller. Use it when CORS must be handled before MVC kicks in, or when the same policy must cover things outside normal MVC controller handling, such as static resources or another servlet mapping.

```java
@Bean
CorsFilter corsFilter() {
	CorsConfiguration config = new CorsConfiguration();
	config.setAllowedOrigins(List.of("https://app.example.com"));
	config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
	config.setAllowedHeaders(List.of("Content-Type", "X-Requested-With"));

	UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
	source.registerCorsConfiguration("/**", config);
	return new CorsFilter(source);
}
```

The decision rule is short: use `WebMvcConfigurer` for nearly everything, use `@CrossOrigin` for one-off scoped exceptions, and use `CorsFilter` only when the policy has to run at the servlet-filter level.

## Origins, Patterns, And Credentials

`allowedOrigins(...)` is exact matching. If you allow `https://app.example.com`, that does not allow `https://www.app.example.com`, `http://app.example.com`, or `https://app.example.com:3000`. This exactness is a feature. CORS is a browser read permission, so vague origin rules are easy to make too permissive.

`allowedOriginPatterns(...)` exists for cases where exact origins are not enough, such as controlled subdomains. Treat it as a sharp tool. A pattern such as `https://*.example.com` might be reasonable for a tenant-owned domain strategy. A pattern that effectively allows the internet is not meaningfully different from `*`.

The wildcard origin `*` means any origin may read the response, but it does not combine with credentials. If your cross-origin request includes cookies, HTTP authentication, or TLS client certificates, the browser requires a specific allowed origin. In Spring terms, `allowCredentials(true)` requires explicit origin matching, not a wildcard. That is why credentialed browser APIs usually need a concrete list of production origins, plus possibly a separate localhost origin for development.

Cookies have one more trap: CORS permission is necessary but not sufficient. A response with `Set-Cookie` still depends on the cookie attributes and browser rules. Cross-site cookies typically need `SameSite=None; Secure`, the frontend request must opt in to credentials, and the server CORS policy must allow credentials with a non-wildcard origin.

## The Sample Controller

This chapter has two endpoints:

```java
{% include-markdown "../../code/13-cors/maven/src/main/java/dev/springboot4docs/ch_13_cors/WidgetController.java" comments=false %}
```

`GET /widgets/all` has no annotation. It uses the global rule from `WebConfig`, which allows only `https://app.example.com`. The response includes `X-Total-Count`, and the global CORS rule exposes that header:

```java
.exposedHeaders("X-Total-Count")
```

Without `Access-Control-Expose-Headers`, browser JavaScript can read only the small set of CORS-safelisted response headers. The response header may be present on the wire, but `fetch(...).headers.get("X-Total-Count")` will not expose it unless the server opts in.

`GET /widgets/local` demonstrates the local annotation style. It allows `https://localhost:3000`, which is a common development frontend origin. It intentionally does not allow `https://app.example.com`, even though the global `/widgets/all` rule does. The endpoint's CORS contract is scoped to that handler.

## The Tests

The test uses the MVC slice plus `MockMvcTester`:

```java
{% include-markdown "../../code/13-cors/maven/src/test/java/dev/springboot4docs/ch_13_cors/WidgetControllerWebMvcTest.java" comments=false %}
```

The first test is the key preflight shape:

```java
this.mvc.options().uri("/widgets/all")
		.header(HttpHeaders.ORIGIN, "https://app.example.com")
		.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
		.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type")
		.exchange();
```

That mirrors what the browser sends. The request method is `OPTIONS`. The `Origin` header is the web page's origin. `Access-Control-Request-Method` tells the server which real method the browser wants to send next. `Access-Control-Request-Headers` lists request headers the browser wants permission to use.

The response is successful and includes CORS response headers:

```java
assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
		.isEqualTo("https://app.example.com");
```

The disallowed preflight test is just as important. A controller can accidentally return `200` to an `OPTIONS` request without making the browser happy. What matters is not "did OPTIONS return a body?" but "did the response grant this origin permission?" The test asserts the response is not in the 2xx range and that `Access-Control-Allow-Origin` is absent.

The actual `GET /widgets/all` test proves that preflight configuration is not the only path. A simple browser request may skip preflight, but it still sends `Origin`, and the actual response still needs `Access-Control-Allow-Origin`. Without it, the browser receives the HTTP response but refuses to expose it to JavaScript.

The local endpoint tests prove the method annotation is scoped: `https://localhost:3000` is allowed for `/widgets/local`, and `https://app.example.com` is rejected for that same endpoint.

Notice what the tests do not do: they do not simulate a JavaScript runtime. The browser behavior is represented by ordinary HTTP headers. That is enough for server-side testing because Spring MVC's CORS processor only sees the incoming method, path, and headers, then decides which response headers or rejection status to produce. Browser automation can still be useful for full frontend flows, but it is usually too slow and indirect for proving backend CORS policy.

Run the chapter tests from the Maven project:

=== "Maven"
    ```bash
    ./mvnw test
    ```

=== "Gradle"
    ```bash
    ./gradlew test
    ```

## Common Bugs

The most common CORS bug is treating `OPTIONS` as the goal. Returning `200` for `OPTIONS` is not enough. The browser needs the right CORS headers, especially `Access-Control-Allow-Origin`, and the value must match the requesting `Origin` or be a valid wildcard response for a non-credentialed request.

Another common bug is origin drift. `https://localhost:3000`, `http://localhost:3000`, and `http://127.0.0.1:3000` are three different origins. If your frontend development server changes host, scheme, or port, the backend CORS rule must match the actual browser `Origin` header.

Credentials cause another cluster of problems. If the frontend calls `fetch(url, { credentials: "include" })`, the server must return `Access-Control-Allow-Credentials: true`, must not use `*` for `Access-Control-Allow-Origin`, and the cookie itself must be usable in a cross-site context. CORS cannot override cookie `SameSite`, `Secure`, or domain rules.

Private Network Access, sometimes discussed as CORS-RFC1918, adds more browser checks when a public site tries to reach a private network address. If a cloud-hosted frontend calls an API at a home, office, or intranet IP, modern browsers may send additional preflight headers and expect additional permission. That is separate from the basic CORS headers in this chapter, but it feels like the same category of failure because the browser blocks the request before application code can read the response.

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

Send a preflight request by hand:

```bash
curl -i -X OPTIONS 'http://localhost:8080/widgets/all' \
  -H 'Origin: https://app.example.com' \
  -H 'Access-Control-Request-Method: GET' \
  -H 'Access-Control-Request-Headers: Content-Type'
```

The response should include headers like these:

```http
HTTP/1.1 200
Access-Control-Allow-Origin: https://app.example.com
Access-Control-Allow-Methods: GET,POST,OPTIONS
Access-Control-Allow-Headers: Content-Type
Access-Control-Max-Age: 1800
```

Then send the actual request:

```bash
curl -i 'http://localhost:8080/widgets/all' \
  -H 'Origin: https://app.example.com'
```

That response includes the JSON body plus the CORS allow-origin header:

```http
HTTP/1.1 200
Access-Control-Allow-Origin: https://app.example.com
Access-Control-Expose-Headers: X-Total-Count
X-Total-Count: 2
Content-Type: application/json

[{"id":1,"name":"Roadrunner"},{"id":2,"name":"Anvil"}]
```

Chapter 14 moves from browser access policy to file upload and download, where the HTTP edge starts dealing with multipart requests, content disposition, and streaming response bodies.
