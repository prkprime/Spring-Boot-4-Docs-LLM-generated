# Your First Test

Every chapter in this guide ships with tests. The goal is not to collect tests as decoration. The goal is to give you a green baseline you can come back to after each change.

When a chapter starts, the tests describe the behavior we already trust. When you edit the application, you run the tests again. If they stay green, you know the old behavior still works. If one turns red, you have a small, concrete problem to investigate instead of a vague feeling that "the app broke."

That rhythm matters more as the guide grows. A one-controller application is easy to hold in your head. A real Spring Boot application has controllers, services, validation, persistence, security, configuration, messaging, and operational behavior. Tests let us move through those layers without rebuilding confidence from scratch each time.

In this chapter we keep the application tiny on purpose. The code only greets a name:

```java
{% include-markdown "../../code/05-your-first-test/maven/src/main/java/dev/springboot4docs/ch_05_your_first_test/Greeting.java" comments=false %}
```

```java
{% include-markdown "../../code/05-your-first-test/maven/src/main/java/dev/springboot4docs/ch_05_your_first_test/GreetingService.java" comments=false %}
```

```java
{% include-markdown "../../code/05-your-first-test/maven/src/main/java/dev/springboot4docs/ch_05_your_first_test/GreetingController.java" comments=false %}
```

The behavior is intentionally small. That lets the tests be the main subject.

## The Spring Boot 4 Testing Toolbox

Spring Boot 4 brings a modern test stack through its test starters. You will see these names throughout the rest of the guide.

**JUnit 6** is the test framework. It finds test classes, runs test methods, reports failures, and integrates with Spring's test support. In the code, the most visible JUnit API is `@Test`.

**AssertJ** is the assertion library. It gives us fluent, chainable assertions such as `assertThat(greeting.text()).isEqualTo("Hello, Spring!")`. This guide uses AssertJ because the assertions read from left to right and produce useful failure messages.

**Mockito** creates test doubles. In this chapter we use it only briefly: the web slice test replaces the real `GreetingService` with a Mockito mock so the controller can be tested by itself.

**`@MockitoBean`** is the Spring test annotation that registers a Mockito mock in the application context. This is the modern annotation from `org.springframework.test.context.bean.override.mockito.MockitoBean`. It replaces the older Spring Boot `@MockBean` style, which was deprecated before the Spring Boot 4 line.

**`MockMvcTester`** is new in Spring Framework 7. It tests Spring MVC through the mock servlet infrastructure and gives us AssertJ-style response assertions. It is the modern replacement for the older `MockMvc.perform(...).andExpect(...)` chain used in many Spring MVC examples.

**`@SpringBootTest`** loads the application for an integration test. In this chapter we use `webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT`, which starts the embedded web server on an available port.

**`RestTestClient`** is new in Spring Framework 7. It is a fluent, blocking test client for HTTP-style tests. This is its first appearance in the guide. When the full application is running on a random port, `RestTestClient` lets the test call the endpoint and make readable expectations about the response.

## Three Layers of Test

Not every test should load the whole application. The smaller the test, the faster it runs and the more directly it points at the broken code. The larger the test, the more confidence it gives that the pieces work together.

Think of the test suite as a pyramid:

```text
           /\
          /  \        integration
         /____\       full app, real HTTP
        /      \
       / slice  \     Spring web layer only
      /__________\
     /            \
    /    unit      \  fastest, no Spring
   /________________\
```

The bottom layer is broad. Unit tests are cheap, fast, and precise.

The middle layer is narrower. Slice tests use Spring, but only for one part of the application.

The top layer is smallest. Integration tests are more expensive, but they prove that the application starts and real requests work.

This chapter has one test at each layer:

- `GreetingServiceTest` is a unit test. It creates the service with `new` and uses no Spring test context.
- `GreetingControllerWebMvcTest` is a web slice test. It loads Spring MVC around `GreetingController`, mocks the service, and verifies the route and JSON response.
- `GreetingApplicationTest` is an integration test. It starts the application with a random HTTP port and calls the real endpoint with `RestTestClient`.

## The Unit Test

Here is the pure unit test:

```java
{% include-markdown "../../code/05-your-first-test/maven/src/test/java/dev/springboot4docs/ch_05_your_first_test/GreetingServiceTest.java" comments=false %}
```

The `package` line places the test in the same package as the application classes. That keeps the test close to the code it verifies.

`org.junit.jupiter.api.Test` is the JUnit annotation for a test method. Even though this guide calls the framework JUnit 6, the core programming model still uses the familiar Jupiter API package.

The static import for `assertThat` brings AssertJ into the test. Static imports are common in tests because they keep the assertion focused on the value under test.

The class has no Spring annotation. There is no `@SpringBootTest`, no `@WebMvcTest`, and no injected fields. That is the defining feature of this layer: it is just Java.

```java
private final GreetingService greetingService = new GreetingService();
```

The test creates the service directly. If the constructor needed collaborators, the test would pass simple fakes or mocks directly to the constructor. Here the service has no dependencies, so `new GreetingService()` is enough.

```java
@Test
void greetsByName() {
```

JUnit runs methods annotated with `@Test`. The method name describes the behavior, not the implementation. We care that the service greets by name.

```java
Greeting greeting = this.greetingService.greet("Spring");
```

This is the act step. The test calls the method with a concrete input and stores the result.

```java
assertThat(greeting.text()).isEqualTo("Hello, Spring!");
```

This is the assertion. AssertJ starts with the actual value, then chains the expectation. If the service returns `"Hello Spring"` without the comma or exclamation mark, the failure message points directly at the mismatched string.

This test is fast because it does not start Spring. Use this layer whenever the behavior can be tested as plain Java.

## The Web Slice Test

The next test checks the controller and the Spring MVC route:

```java
{% include-markdown "../../code/05-your-first-test/maven/src/test/java/dev/springboot4docs/ch_05_your_first_test/GreetingControllerWebMvcTest.java" comments=false %}
```

This test uses Spring, but it does not use the full application.

```java
@WebMvcTest(GreetingController.class)
```

`@WebMvcTest` creates a Spring MVC slice. The test context contains the web infrastructure needed to route a request to `GreetingController`, serialize the returned `Greeting`, and produce an HTTP-style response. It does not load every service bean in the application.

That is why this field exists:

```java
@MockitoBean
private GreetingService greetingService;
```

The controller needs a `GreetingService` constructor argument. A web slice does not load the real service, so the test supplies a Mockito mock as a Spring bean. `@MockitoBean` is the modern Spring Framework test annotation for that job.

```java
@Autowired
private MockMvcTester mvc;
```

Spring Boot's web MVC test support autoconfigures `MockMvcTester` for the slice. The tester sends requests through Spring MVC's mock servlet environment. There is no real network socket here and no embedded server port.

Inside the test method, Mockito defines the service behavior:

```java
when(this.greetingService.greet("Spring")).thenReturn(new Greeting("Hello, Spring!"));
```

This says: if the controller asks the service to greet `"Spring"`, return this `Greeting`. The test is not checking the service algorithm. The unit test already does that. The slice test checks whether the web layer receives the path variable, calls the service, and writes the response.

The request starts here:

```java
this.mvc.get().uri("/greet/{name}", "Spring")
```

`get()` creates a GET request. The URI template keeps the test close to the controller mapping, and `"Spring"` fills `{name}`.

```java
.assertThat()
```

`MockMvcTester` returns an AssertJ-aware result. From this point, the chain is assertions rather than setup.

```java
.hasStatusOk()
```

The route should return HTTP 200. This proves the request matched a handler and the handler completed successfully.

```java
.hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
```

The controller returns a `Greeting` record, and Spring MVC serializes it to JSON. This assertion checks that the response is JSON-compatible.

```java
.bodyJson()
.extractingPath("$.text")
.isEqualTo("Hello, Spring!");
```

The JSON body should contain a `text` field. The JSONPath expression `$.text` selects that field, and AssertJ checks its value.

A slice test is the right place to test routing, request binding, validation errors, response status codes, response headers, and JSON shape for a controller. It is not the right place to prove that the whole application can start.

## The Integration Test

The final test starts the full application:

```java
{% include-markdown "../../code/05-your-first-test/maven/src/test/java/dev/springboot4docs/ch_05_your_first_test/GreetingApplicationTest.java" comments=false %}
```

This class begins with:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
```

`@SpringBootTest` asks Spring Boot to load the application context from `Application`. The `RANDOM_PORT` web environment starts the embedded servlet server on an available port instead of the default `8080`. Tests should not assume that `8080` is free on every developer machine or CI runner.

```java
@AutoConfigureRestTestClient
```

This enables Spring Boot's test autoconfiguration for `RestTestClient`. The test can then inject the client instead of manually building it.

```java
@Autowired
private RestTestClient restTestClient;
```

`RestTestClient` is the HTTP-style test client we will use for full application tests in this guide. It is fluent, blocking, and designed for tests. The blocking part is useful here: the test sends a request, waits for the response, and immediately asserts the result.

The request looks similar to the slice test:

```java
this.restTestClient.get().uri("/greet/{name}", "Spring")
```

The difference is the layer underneath. In the slice test, `MockMvcTester` drives Spring MVC without opening a port. In this integration test, the application has been started as a web application.

```java
.exchange()
```

`exchange()` sends the request and returns a response specification.

```java
.expectStatus().isOk()
```

The fluent API keeps status assertions close to the request.

```java
.expectBody()
.jsonPath("$.text").isEqualTo("Hello, Spring!");
```

The body assertion checks the JSON returned by the real endpoint. This test covers the controller, the real service, JSON serialization, application startup, and the web server configuration needed to serve the endpoint.

That broader coverage is why we keep integration tests fewer and more intentional. They are valuable, but they are not a replacement for fast unit and slice tests.

## MockMvcTester vs RestTestClient

Use both tools, but use them for different jobs.

| Tool | Best for | Server model | Typical annotation | Who configures it |
| --- | --- | --- | --- | --- |
| `MockMvcTester` | MVC slice tests for controllers, request binding, status codes, headers, and JSON shape | Mock servlet environment, no real port | `@WebMvcTest` | Spring Boot web MVC test autoconfiguration |
| `RestTestClient` | Full application tests that call endpoints through the running app | Random HTTP port with `@SpringBootTest` | `@SpringBootTest(webEnvironment = RANDOM_PORT)` plus `@AutoConfigureRestTestClient` | Spring Boot RestTestClient test autoconfiguration |

The practical rule is simple: use `MockMvcTester` when you want to stay inside the web layer and mock the controller's dependencies. Use `RestTestClient` when you want to prove the application starts and the endpoint works through the real HTTP boundary.

## What About TestRestTemplate?

You may see `TestRestTemplate` in older Spring Boot tests. It has been around for a long time and still appears in existing projects and blog posts.

This guide uses `RestTestClient` instead. It is the modern Spring Framework 7 client for this style of test, and its fluent API fits the AssertJ-oriented testing style used throughout the guide. We will not use `TestRestTemplate` in later chapters.

## Run It

Run the tests from the chapter project:

```bash
cd code/05-your-first-test/maven
./mvnw -q -B test
```

A green run means all three layers agree:

- the service returns the expected `Greeting`;
- the MVC route maps `/greet/{name}` and writes JSON;
- the full application starts and serves the endpoint over HTTP.

If a test fails, start with the layer. A unit test failure usually points at plain Java behavior. A slice test failure usually points at MVC mapping, JSON, or mocked collaboration. An integration test failure may involve wiring, application startup, server configuration, or behavior that only appears when the pieces run together.

## Next

Chapter 6 starts the REST controller work in earnest. The testing vocabulary from this chapter carries forward into that part of the guide: unit tests for small behavior, web slices for controller contracts, and full application tests when the real HTTP boundary matters.

Part II builds out the web layer from there. Keep these three tools close: they are the baseline for changing controllers without guessing.
