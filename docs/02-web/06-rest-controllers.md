# REST Controllers

In this chapter we will build a CRUD-ish Books API with list, read, create, replace, delete, and search endpoints.

REST controllers are the usual entry point for HTTP APIs in a Spring MVC application. They translate an HTTP request into Java method arguments, call application code, and translate the Java return value back into an HTTP response. In this chapter the application code is intentionally small: an in-memory repository backed by a `ConcurrentHashMap`. That keeps the focus on the controller vocabulary.

The API will expose six routes:

```text
GET    /books
GET    /books/{id}
POST   /books
PUT    /books/{id}
DELETE /books/{id}
GET    /books/search?title=...&author=...
```

Those routes are enough to cover the main Spring MVC annotations you will use in REST controllers. The collection routes work with `/books`. The member routes work with `/books/{id}`. The search route demonstrates query parameters without mixing them into the main collection listing.

The domain type is a Java record:

```java
{% include-markdown "../../code/06-rest-controllers/maven/src/main/java/dev/springboot4docs/ch_06_rest_controllers/Book.java" comments=false %}
```

Spring MVC and Jackson can serialize records directly. A `Book` returned from a controller becomes JSON with `id`, `title`, and `author` fields. A JSON request body with `title` and `author` fields can also be read into a `Book`. We will go deeper on JSON naming, dates, custom serialization, and Jackson 3 configuration in chapter 7.

## The Repository

The repository is a simple Spring component:

```java
{% include-markdown "../../code/06-rest-controllers/maven/src/main/java/dev/springboot4docs/ch_06_rest_controllers/BookRepository.java" comments=false %}
```

`@Component` makes `BookRepository` a Spring bean. The controller receives it through constructor injection.

The storage field is a `ConcurrentHashMap`, exposed through the `ConcurrentMap` interface. That is enough for a demo repository that may be called by more than one request thread. The `AtomicLong` gives each new book a generated id. This is not a persistence chapter, so there is no database yet.

`findAll()` returns a fresh `ArrayList` copy of the map values. That avoids handing callers the collection view owned by the map.

`findById(Long id)` returns `Optional<Book>`. The repository does not decide what HTTP status a missing book should produce. It only says whether the data exists.

`create(Book book)` ignores the incoming id and assigns a new one. That keeps POST semantics simple: the client sends the representation it wants to create, and the server decides the resource identifier.

`update(Long id, Book book)` performs a full replacement at a known resource URI. The id comes from the path, not from the request body. If the map does not already contain that id, the method returns `Optional.empty()`.

`deleteById(Long id)` removes the entry if it exists. This controller will return `204 No Content` either way. That is a common, practical choice for a delete endpoint.

`search(Optional<String> title, Optional<String> author)` applies optional filters. If a query parameter is absent, its filter is skipped. If both are absent, all books match.

## RestController

Here is the complete controller:

```java
{% include-markdown "../../code/06-rest-controllers/maven/src/main/java/dev/springboot4docs/ch_06_rest_controllers/BookController.java" comments=false %}
```

`@RestController` is a convenience annotation. It combines `@Controller` with `@ResponseBody` behavior for every handler method in the class. A regular `@Controller` is often used for server-rendered views: a return value such as `"books/list"` might mean "render this template." A `@RestController` treats return values as response bodies, so returning `List<Book>` means "write these books to the HTTP response."

That distinction is important because the Java return type does not say whether the application is rendering HTML or writing JSON. The annotation supplies that intent. In a view controller, a `String` can be a view name. In a REST controller, a `String` is response content. In a view controller, a model attribute might be used by a template. In a REST controller, the returned object is handed to an HTTP message converter.

Message converters are the bridge between Java values and HTTP payloads. In this project, the web MVC starter brings JSON support, so Spring MVC can write `Book` records as JSON and read JSON bodies back into `Book` records. The controller does not call Jackson directly. It declares Java method signatures, and Spring MVC chooses the converter based on the request and response.

!!! note "Spring Boot 4"
    This chapter uses Spring Boot 4's servlet MVC starter, `spring-boot-starter-webmvc`. The test slice uses `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`, the modularized Spring Boot 4 package.

The constructor is ordinary Java constructor injection:

```java
public BookController(BookRepository bookRepository) {
	this.bookRepository = bookRepository;
}
```

Spring sees that `BookController` needs a `BookRepository` and supplies the repository bean when it creates the controller.

The first endpoint is the collection read:

```java
@GetMapping("/books")
public List<Book> list() {
	return this.bookRepository.findAll();
}
```

`@GetMapping` maps HTTP `GET /books` to this method. Returning `List<Book>` is enough for a `200 OK` response with a JSON array body.

The single-resource read uses a path variable:

```java
@GetMapping("/books/{id}")
public ResponseEntity<Book> get(@PathVariable Long id) {
	return this.bookRepository.findById(id)
			.map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.notFound().build());
}
```

`{id}` in the path template is bound to the `id` parameter by `@PathVariable`. Spring converts the path segment from text to `Long`. The method returns `ResponseEntity<Book>` because it needs to choose between two different HTTP responses: `200 OK` with a book, or `404 Not Found` with no body.

!!! info "Error bodies come later"
    This chapter returns a plain 404 for a missing book. Chapter 9 introduces `ProblemDetail` and structured error responses.

The create endpoint reads a JSON request body:

```java
@PostMapping("/books")
public ResponseEntity<Book> create(@RequestBody Book book) {
	Book saved = this.bookRepository.create(book);
	URI location = ServletUriComponentsBuilder.fromCurrentRequest()
			.path("/{id}")
			.buildAndExpand(saved.id())
			.toUri();
	return ResponseEntity.created(location).body(saved);
}
```

`@PostMapping("/books")` maps `POST /books`. `@RequestBody` tells Spring MVC to read the HTTP request body and convert it into a `Book`. Because the request content type is JSON, the configured Jackson HTTP message converter does the conversion.

The response uses `ResponseEntity.created(location)`, which sets status `201 Created` and writes a `Location` header. `ServletUriComponentsBuilder.fromCurrentRequest()` starts with the current request URI, so a POST to `/books` can build `/books/{id}` for the newly created resource. The response body also returns the saved book, including its generated id.

The replacement endpoint is a PUT:

```java
@PutMapping("/books/{id}")
public ResponseEntity<Book> update(@PathVariable Long id, @RequestBody Book book) {
	return this.bookRepository.update(id, book)
			.map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.notFound().build());
}
```

`PUT /books/{id}` means "replace the resource at this URI." The id is still taken from the path. The JSON body supplies the new representation. This method returns `200 OK` with the updated book when the id exists, or `404 Not Found` when it does not.

The delete endpoint is:

```java
@DeleteMapping("/books/{id}")
public ResponseEntity<Void> delete(@PathVariable Long id) {
	this.bookRepository.deleteById(id);
	return ResponseEntity.noContent().build();
}
```

`@DeleteMapping` maps HTTP DELETE. The return type is `ResponseEntity<Void>` because there is no response body. `ResponseEntity.noContent()` sets status `204 No Content`.

The search endpoint uses query parameters:

```java
@GetMapping("/books/search")
public List<Book> search(@RequestParam Optional<String> title, @RequestParam Optional<String> author) {
	return this.bookRepository.search(title, author);
}
```

Query parameters are the `?name=value` part of the URI. `@RequestParam Optional<String> title` binds `?title=...` if present and gives `Optional.empty()` if absent. This endpoint accepts `/books/search`, `/books/search?title=Dune`, `/books/search?author=Frank`, or `/books/search?title=Dune&author=Frank`.

The composed mapping annotations are the common style: `@GetMapping`, `@PostMapping`, `@PutMapping`, and `@DeleteMapping`. They are shortcuts for the older, more general `@RequestMapping` form:

```java
@RequestMapping(path = "/books", method = RequestMethod.GET)
```

Use the composed annotations when the HTTP method is known. Use `@RequestMapping` when you need a shared class-level prefix, multiple methods, or a less common mapping shape.

The path is only one part of request mapping. Spring MVC can also map by headers, query parameters, and content type. You will see that in later chapters when validation and content negotiation become more important. For now, keep the route definitions direct: one annotation per handler, with the HTTP method and URI pattern visible at the top of the method.

The annotations on method parameters describe where each value comes from. `@PathVariable` reads from the URI template, `@RequestParam` reads from the query string or form parameters, and `@RequestBody` reads the request payload. Keeping those sources explicit makes controller methods easy to scan. It also prevents a common beginner mistake: putting an id in both the path and the JSON body, then wondering which one wins. In this chapter the path owns the id for reads, updates, and deletes.

## Status Codes

There are two common ways to set status codes in a Spring MVC controller.

Use `ResponseEntity` when the status, headers, or body vary per request. This chapter uses it for `GET /books/{id}`, `POST /books`, `PUT /books/{id}`, and `DELETE /books/{id}`. `ResponseEntity` is also the right tool for the `Location` header on `201 Created`.

Use `@ResponseStatus` when the status is fixed for a handler or exception type. For example, a create method could be annotated with `@ResponseStatus(HttpStatus.CREATED)` if it always returned `201` and did not need a `Location` header. In real APIs, creates usually should return the new resource URI, so `ResponseEntity.created(location)` is more expressive.

!!! warning
    Do not return `200 OK` for every successful request by habit. HTTP clients can use `201 Created`, `204 No Content`, and `404 Not Found` without parsing the body.

## Controller Tests

The controller test is a Spring MVC slice:

```java
{% include-markdown "../../code/06-rest-controllers/maven/src/test/java/dev/springboot4docs/ch_06_rest_controllers/BookControllerWebMvcTest.java" comments=false %}
```

`@WebMvcTest(BookController.class)` loads the MVC infrastructure and the selected controller. It does not load the real repository. The repository field is annotated with `@MockitoBean`, so Spring registers a Mockito mock as the `BookRepository` bean needed by the controller.

That boundary is deliberate. A controller slice test should answer web-layer questions: does the route match, are path variables and query parameters bound correctly, is the request body deserialized, does the controller choose the right status, and does the response have the expected JSON and headers? It should not depend on the repository's in-memory map behavior. If a list test fails in this class, the failure should point at the controller mapping or response, not at storage.

!!! note "MockitoBean"
    Use `@MockitoBean` from `org.springframework.test.context.bean.override.mockito.MockitoBean`. The older Spring Boot `@MockBean` style is deprecated and is not used in these Spring Boot 4 chapters.

```java
@Autowired
private MockMvcTester mvc;
```

`MockMvcTester` sends requests through Spring MVC's mock servlet environment. There is no network port. The request still goes through request mapping, argument binding, message conversion, and response rendering.

The test setup uses Mockito's `when(...).thenReturn(...)` to describe repository results. For example, the controller can be tested with a found book, a missing book, or a filtered search result without preparing real repository state. That keeps each test short and focused on one HTTP behavior.

The list test shows a status assertion and a JSON array assertion:

```java
this.mvc.get().uri("/books")
		.assertThat()
		.hasStatusOk()
		.hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
		.bodyJson()
		.extractingPath("$")
		.asArray()
		.hasSize(2);
```

`get().uri("/books")` builds the request. `assertThat()` switches into AssertJ-style response assertions. `bodyJson().extractingPath("$").asArray().hasSize(2)` reads the whole JSON document as an array and checks the number of elements.

The single-resource test shows a JSON field assertion:

```java
this.mvc.get().uri("/books/{id}", 1)
		.assertThat()
		.hasStatusOk()
		.hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
		.bodyJson()
		.extractingPath("$.title")
		.isEqualTo("Dune");
```

The URI template fills `{id}` with `1`. The JSONPath expression `$.title` selects the `title` field from the response object.

The create test checks the `201 Created` status and the `Location` header:

```java
this.mvc.post().uri("/books")
		.contentType(MediaType.APPLICATION_JSON)
		.content("""
				{"title":"Dune","author":"Frank Herbert"}
				""")
		.assertThat()
		.hasStatus(201)
		.hasHeader(HttpHeaders.LOCATION, "http://localhost/books/1")
		.bodyJson()
		.extractingPath("$.id")
		.isEqualTo(1);
```

The request sets `Content-Type: application/json` because the controller parameter is annotated with `@RequestBody Book book`. The `Location` header is absolute in the test because the mock servlet request has `http://localhost` as its base URL.

The delete test first sends `DELETE /books/1`, verifies `204 No Content`, and then sends `GET /books/1` with the mock configured to return `Optional.empty()`. That proves the controller maps the missing repository result to `404 Not Found`.

## Run It

Start the application from the chapter directory:

=== "Maven"
    ```bash
    cd code/06-rest-controllers/maven
    ./mvnw spring-boot:run
    ```

=== "Gradle"
    ```bash
    cd code/06-rest-controllers/gradle
    ./gradlew bootRun
    ```

Create a book:

```bash
curl -i -X POST http://localhost:8080/books \
  -H 'Content-Type: application/json' \
  -d '{"title":"Dune","author":"Frank Herbert"}'
```

The response should be `201 Created` and include a `Location` header like `http://localhost:8080/books/1`.

List all books:

```bash
curl -i http://localhost:8080/books
```

Read one book:

```bash
curl -i http://localhost:8080/books/1
```

Replace the book:

```bash
curl -i -X PUT http://localhost:8080/books/1 \
  -H 'Content-Type: application/json' \
  -d '{"title":"Dune Messiah","author":"Frank Herbert"}'
```

Search with query parameters:

```bash
curl -i 'http://localhost:8080/books/search?title=Dune&author=Frank'
```

Delete the book:

```bash
curl -i -X DELETE http://localhost:8080/books/1
```

Run the tests:

=== "Maven"
    ```bash
    ./mvnw test
    ```

=== "Gradle"
    ```bash
    ./gradlew test
    ```

The important controller vocabulary is now in place: mapping annotations choose the HTTP verb and path, `@PathVariable` reads path segments, `@RequestParam` reads query parameters, `@RequestBody` reads JSON, and `ResponseEntity` controls status codes and headers. Chapter 7 keeps the same HTTP foundation and looks more closely at the JSON layer underneath it.
