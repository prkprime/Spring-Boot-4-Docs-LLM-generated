# Filters & Interceptors

Spring MVC gives you two related extension points around a request: servlet filters and MVC handler interceptors. They are easy to confuse because both can run code before and after your controller. The important difference is where they run.

A `Filter` is part of the servlet container's filter chain. It runs before the request reaches Spring MVC's `DispatcherServlet`, so it can see every servlet request that matches its registration: controllers, static resources, error dispatches, and anything else served through the same web application.

A `HandlerInterceptor` is part of Spring MVC. It runs inside the `DispatcherServlet`, after MVC has selected a handler. That means an interceptor can see the matched controller method, but only for requests that actually reach MVC handler mapping.

```text
request
  -> servlet filters
       -> DispatcherServlet
            -> HandlerInterceptor.preHandle
                 -> controller
            -> HandlerInterceptor.postHandle
            -> HandlerInterceptor.afterCompletion
       <- DispatcherServlet
  <- servlet filters finish
response
```

Use filters when the work belongs at the HTTP edge and does not need Spring MVC's selected handler. Request correlation IDs, low-level request logging, compression, pre-security checks, and similar transport concerns fit well there. Use interceptors when the work depends on MVC context: the matched `HandlerMethod`, controller annotations, selected path pattern, or per-handler metrics.

!!! note "Spring Security"
    Spring Security's servlet support also lives in the servlet filter chain. We will spend proper time with that in the security chapters, starting around chapter 26. For now, keep the mental model simple: security filters are filters too, and ordering matters.

## Request IDs

The sample filter generates one request ID per request, stores it in SLF4J's MDC, puts it on the servlet request as an attribute, and writes it to the response as `X-Request-Id`:

```java
{% include-markdown "../../code/12-filters-interceptors/maven/src/main/java/dev/springboot4docs/ch_12_filters_interceptors/RequestIdFilter.java" comments=false %}
```

The servlet API is Jakarta in Spring Boot 4 applications, so the imports come from `jakarta.servlet.*`, not `javax.servlet.*`. That is not cosmetic. Spring Framework 6 and later moved to Jakarta EE 9+ package names, and Spring Framework 7 continues that line.

The MDC pattern is the most important detail in this filter:

```java
MDC.put("requestId", requestId);
try {
	chain.doFilter(request, response);
}
finally {
	MDC.remove("requestId");
}
```

MDC values are usually stored in thread-local state. If you put a value in MDC and forget to remove it, a later request handled on the same platform thread can inherit the wrong logging context. Always clean up in `finally`, because controller code can throw, validation can fail, and the response can be committed through an error path. The cleanup must happen regardless.

The filter also sets the response header before calling the rest of the chain. That makes the correlation ID visible to clients, tests, gateways, and logs. The controller reads the request attribute only so the sample response can show the same ID in the body; the filter is still the owner of the ID.

## Filter Registration

Spring Boot can auto-register filters that are beans. If you annotate a `Filter` with `@Component`, Boot will find it and add it to the servlet filter chain. That is convenient for small applications, but ordering and URL scoping are usually part of the filter's contract.

This chapter uses a `FilterRegistrationBean<RequestIdFilter>` instead:

```java
{% include-markdown "../../code/12-filters-interceptors/maven/src/main/java/dev/springboot4docs/ch_12_filters_interceptors/WebConfig.java" comments=false %}
```

`setOrder(Ordered.HIGHEST_PRECEDENCE)` puts the request ID filter near the front of the filter chain. That is useful because downstream filters, controllers, and exception handling can all log with the same correlation ID. `addUrlPatterns("/*")` applies it to the whole servlet application. In a larger application, you might scope a filter to `/api/*`, `/actuator/*`, or a legacy servlet path.

The registration bean is also where you would set dispatcher types or init parameters for a more advanced servlet filter. Most Spring MVC applications do not need that much servlet-level customization, but filters are still servlet components, so their registration follows servlet rules.

One practical consequence is that filters can affect traffic your controller tests never mention. If the application serves static assets, health pages, or framework endpoints through the same servlet environment and the URL pattern matches them, the filter participates. That is useful for correlation IDs because every response can carry the same header. It is risky for behavior that assumes a JSON API request. Keep servlet filters defensive: check request types, methods, paths, and headers before applying API-specific logic.

## Timing Interceptor

The interceptor records a start time before the controller runs. After MVC completes the request, it calculates elapsed time, writes an `X-Elapsed-Ms` header, and logs the matched handler:

```java
{% include-markdown "../../code/12-filters-interceptors/maven/src/main/java/dev/springboot4docs/ch_12_filters_interceptors/TimingInterceptor.java" comments=false %}
```

`preHandle` runs before the handler method. Returning `true` lets the request continue. Returning `false` stops handler execution, which is useful for some authorization or precondition checks, but that is not what this sample needs.

`postHandle` runs after the handler returns but before view rendering. For REST controllers, there may not be much view work to do, because the return value is written by an HTTP message converter.

`afterCompletion` runs after request processing completes. It runs even when the handler throws an exception, so it is a good place for cleanup and final timing logs. This sample sets `X-Elapsed-Ms` there because the test needs a deterministic signal that the interceptor ran.

The handler object is especially important. In MVC controller requests it is often a `HandlerMethod`, which gives you the controller class and Java method:

```java
if (handler instanceof HandlerMethod handlerMethod) {
	log.debug("Handling {} with {}", request.getRequestURI(), describe(handlerMethod));
}
```

That is the main reason to choose an interceptor over a filter for per-handler behavior. A filter sees a servlet request and response. An interceptor can know that `/api/echo` was handled by `EchoController#echo`, and it can inspect annotations on that method or controller class.

## Interceptor Registration

The same `WebConfig` class registers the interceptor through `WebMvcConfigurer`:

```java
{% include-markdown "../../code/12-filters-interceptors/maven/src/main/java/dev/springboot4docs/ch_12_filters_interceptors/WebConfig.java" comments=false %}
```

`addInterceptors` adds the `TimingInterceptor` to Spring MVC. The important part is the path pattern:

```java
registry.addInterceptor(this.timingInterceptor)
		.addPathPatterns("/api/**");
```

Only MVC handler invocations under `/api/**` get this interceptor. A static file such as `/logo.png` can still pass through servlet filters, but it will not hit this interceptor unless it is mapped as an MVC handler and matches the pattern. That is the lifecycle distinction made concrete.

Path patterns should stay close to the concern. If you are collecting API metrics, `/api/**` is likely right. If you are implementing admin-only checks, `/admin/**` might be right. Avoid making every interceptor global just because it is easy; global interceptors become invisible coupling across unrelated controllers.

## Controller

The controller is deliberately small:

```java
{% include-markdown "../../code/12-filters-interceptors/maven/src/main/java/dev/springboot4docs/ch_12_filters_interceptors/EchoController.java" comments=false %}
```

`GET /api/echo?msg=hello` returns a JSON object with the message and the request ID that the filter placed on the request. The controller does not create the request ID and does not know how it is registered. It only consumes data already attached to the current request.

That separation is the design point. The controller owns the application endpoint. The filter owns request correlation. The interceptor owns MVC handler timing. Each extension point does one job at the right layer.

## Tests

The test uses the Spring Boot MVC slice and `MockMvcTester`:

```java
{% include-markdown "../../code/12-filters-interceptors/maven/src/test/java/dev/springboot4docs/ch_12_filters_interceptors/EchoControllerWebMvcTest.java" comments=false %}
```

`@WebMvcTest(EchoController.class)` loads the MVC test slice around the controller. The test imports `TimingInterceptor` and `WebConfig` because the point is not just to instantiate the controller; it is to prove that the real MVC registration path runs. The interceptor is added through `WebMvcConfigurer`, and the request matches `/api/**`, so the response should contain `X-Elapsed-Ms`.

The same test also asserts `X-Request-Id`. That header comes from the servlet filter, not the controller. The controller response body proves the handler ran, while the two headers prove both surrounding extension points participated in the request.

This is a focused test. It does not capture logs, sleep to make elapsed time nonzero, or assert a UUID format. The important contract is that the filter adds a request ID header and the interceptor registered for `/api/**` adds an elapsed-time header.

Run the chapter tests from the Maven project:

=== "Maven"
    ```bash
    ./mvnw test
    ```

=== "Gradle"
    ```bash
    ./gradlew test
    ```

## Choosing One

Choose a filter when the concern belongs before Spring MVC or applies outside MVC controllers. A filter is the right shape for a request ID because every later component benefits from that value. It is also the right layer for servlet-level request logging, compression, or coarse checks that should happen before controller mapping.

Choose an interceptor when the concern needs the selected handler. Per-controller timing, annotation-driven authorization, feature flags attached to handler methods, or metrics tagged by controller and method all need MVC knowledge. That knowledge does not exist before the `DispatcherServlet` maps the request.

There is overlap. You can log timing in a filter, and you can add headers in an interceptor. The question is what information the code needs and how early it must run. Pick the extension point whose lifecycle naturally contains the data you need.

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

Call the endpoint:

```bash
curl -i 'http://localhost:8080/api/echo?msg=hello'
```

The response includes the request ID from the filter and the elapsed-time header from the interceptor:

```http
HTTP/1.1 200
X-Request-Id: 3ce4caca-1f8f-4a70-a1be-523ef740d06e
X-Elapsed-Ms: 2
Content-Type: application/json

{"message":"hello","requestId":"3ce4caca-1f8f-4a70-a1be-523ef740d06e"}
```

Chapter 13 moves to CORS, another web-edge concern, but one with browser-specific rules and preflight requests.
