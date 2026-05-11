# Part III — Data

Persistence with Spring Data JPA. We jump from H2 to **Postgres + Testcontainers + Flyway** in chapter 18 — early, so dialect, migrations, and locking aren't surprises later.

By the end of Part III you will:

- Have JPA entities, repositories, derived queries, and projections.
- Be running every test against a real Postgres in Docker via Testcontainers.
- Know how to manage relationships without N+1, declare transactions correctly, paginate, build dynamic queries with Specifications, audit changes, and add caching (in-process Caffeine, then distributed Redis).

Chapters:

17. [Spring Data JPA Basics](17-jpa-basics.md) — H2, the smallest possible CRUD repository.
18. [Postgres + Testcontainers + Flyway](18-postgres-testcontainers.md) — moving to a real database.
19. [Relationships & N+1](19-relationships.md)
20. [Transactions](20-transactions.md)
21. [Pagination & Sorting](21-pagination.md)
22. [Specifications](22-specifications.md)
23. [Auditing & Soft Deletes](23-auditing.md)
24. [Caching with Caffeine](24-caching-caffeine.md)
25. [Caching with Redis](25-caching-redis.md)
