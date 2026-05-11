# Appendix A — WebFlux

Spring MVC is the primary path in this course because it is the common default for request/response business APIs. WebFlux is Spring's reactive web stack. It is useful when the application must hold many concurrent I/O-bound connections, stream data, or compose reactive client calls end to end.

The mental model changes. MVC methods usually return values directly:

```java
@GetMapping("/customers/{id}")
Customer customer(@PathVariable long id) {
    return service.findCustomer(id);
}
```

WebFlux methods usually return `Mono<T>` for zero-or-one values or `Flux<T>` for streams:

```java
@GetMapping("/customers/{id}")
Mono<Customer> customer(@PathVariable long id) {
    return service.findCustomer(id);
}
```

Do not mix a reactive controller with blocking database calls and expect scalability. If the call path is reactive, use reactive infrastructure through the stack: `WebClient` for HTTP clients and R2DBC for relational database access. JPA is blocking. It belongs naturally with MVC and regular JDBC connection pools.

Use WebFlux when the whole workflow benefits from non-blocking I/O. Use MVC when the app is a normal CRUD API, especially with JPA. Spring Boot 4 supports both, but most teams should choose one web stack per application.

