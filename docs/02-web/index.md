# Part II — Web Layer (MVC)

The HTTP edge of your application. Eleven chapters covering everything from `GET /hello` to streaming file uploads, with the two genuine Spring Boot 4 newcomers — **API Versioning auto-config** and **HTTP Service Clients auto-config** — getting their own dedicated chapters.

By the end of Part II you will have:

- A REST API with proper HTTP semantics: verbs, status codes, content negotiation.
- Robust JSON handling with Jackson 3 — records, dates, money, custom (de)serializers.
- Inputs validated by Bean Validation 3.1 with clean error responses via `ProblemDetail` (RFC 9457).
- Versioned endpoints using SB4's new `ApiVersionStrategy`.
- Outbound HTTP via declarative `@HttpExchange` interfaces.
- A handle on filters, interceptors, CORS, file I/O, async + SSE + virtual threads.
- OpenAPI / Swagger UI for documentation (only — we drive examples with curl/httpie/tests).

Chapters:

6. [REST Controllers](06-rest-controllers.md)
7. [JSON with Jackson 3](07-json-jackson.md)
8. [Bean Validation 3.1](08-bean-validation.md)
9. [Error Handling + ProblemDetail](09-error-handling.md)
10. [API Versioning (NEW)](10-api-versioning.md)
11. [HTTP Service Clients (NEW)](11-http-service-clients.md)
12. [Filters & Interceptors](12-filters-interceptors.md)
13. [CORS](13-cors.md)
14. [File Upload & Download](14-file-io.md)
15. [Async + SSE + Virtual Threads](15-async-sse-virtual-threads.md)
16. [OpenAPI Documentation](16-openapi.md)
