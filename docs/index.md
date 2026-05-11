# Spring Boot 4 — Follow-Along

A from-zero, line-by-line guide to **Spring Boot 4** (released November 2025), built on Spring Framework 7 and the Java 25 LTS. Every code chapter ships a working Maven project exercised by JUnit 6 tests so you always have a green baseline to come back to.

This guide is opinionated: it teaches **only Spring Boot 4 idioms**. No SB2 or SB3 patterns are presented as "the right way." Where Boot 4 deprecates or replaces something, we go straight to the replacement.

## Who this is for

You should already be comfortable with:

- **Java basics** — classes, packages, generics, lambdas, records, the build/run loop.
- **HTTP fundamentals** — verbs (`GET`/`POST`/`PUT`/`DELETE`), status codes, headers, JSON request/response bodies.
- **Your terminal** — running `curl` or `httpie`, editing files, reading logs.

You do **not** need prior Spring experience. Reactive, dependency injection, auto-configuration, the servlet model — all introduced from scratch when needed.

## How to read this guide

- **In order.** Chapter N assumes everything from chapters 1…N−1.
- **With a terminal open.** Each chapter ships a runnable project under `code/NN-slug/`. Run it, hit it with `curl`, then read the prose.
- **Use Maven for now.** Gradle samples are planned, but Spring Initializr's Gradle generation is currently failing upstream. The verification scripts already skip Gradle when `gradle/` is absent.

## Toolchain

| Tool | Required version | Notes |
| --- | --- | --- |
| JDK | **17 minimum**, **25 recommended (LTS)** | Samples target Java 25. SB 4 will run on 17, but the doc & samples assume 25 features. |
| Maven | bundled wrapper (`./mvnw`) | No system install needed. |
| Gradle | pending backfill | Planned once Spring Initializr Gradle generation recovers. |
| Docker | required from Part III ch. 18 onward | Used for Postgres, Redis, Keycloak via Testcontainers. |
| Spring Boot | **4.0.6** (current GA at time of writing) | Pinned in every sample's build files. |

## Layout of the guide

- **Part I — Foundations.** Hello world, dev loop, configuration, what's actually new in SB4, your first test.
- **Part II — Web layer.** REST controllers, JSON (Jackson 3), validation, error handling with `ProblemDetail`, API versioning, HTTP service clients, filters, CORS, file I/O, async + virtual threads, OpenAPI.
- **Part III — Data.** JPA basics with H2, then straight to **Postgres + Testcontainers + Flyway** so you learn migrations and dialects up front. Relationships, transactions, pagination, caching with Caffeine and Redis.
- **Part IV — Security ladder.** Spring Security 7 fundamentals → HTTP Basic → form login → JDBC users → method security → JWT (resource server first, then a clearly-labelled non-production demo issuer) → OAuth2 login → OAuth2 resource server with Keycloak → LDAP.
- **Part V — Production-ready.** Profiles, structured logging, Actuator, Micrometer + Prometheus + OpenTelemetry, graceful shutdown, the testing trilogy (unit / slice / Testcontainers), virtual-thread tuning, Docker, GitHub Actions CI, Fly.io deploy.
- **Part VI — Capstone.** A complete Tasks API: end-to-end production app deployed to Fly.io.
- **Part VII — Mini-projects.** Passkeys / WebAuthn as its own end-to-end project (HTTPS/origin/recovery is too much for one chapter).
- **Appendices.** WebFlux (Part II/III restated reactively), GraalVM native, Kubernetes primer, SB3→SB4 migration, programmatic bean registration.

## What this guide does **not** cover

- **Spring Boot 2 or 3.** Migration from 3 → 4 is a single appendix. If you have an SB3 codebase, read that appendix first.
- **Frontend development.** Backend only. We use `curl`, `httpie`, and JUnit/MockMvc/`RestTestClient` to drive examples. Swagger UI is for *reading* the API, not *firing* requests.
- **Reactive everywhere.** WebFlux is an appendix. The main path is Spring MVC + virtual threads.

## Building this site locally

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install mkdocs-material mkdocs-include-markdown-plugin \
            mkdocs-git-revision-date-localized-plugin mkdocs-glightbox
mkdocs serve
```

Open <http://127.0.0.1:8000>.

## Conventions used in this guide

- Code that you should type or run is in fenced blocks with the language tagged. Output blocks are tagged `text` and labelled "Output".
- New concepts get a `!!! note` callout the first time they appear and a back-reference thereafter.
- "Production aside" callouts (`!!! warning`) flag things that work in samples but bite in production.
- Every chapter ends with a **Run it** section: the exact commands that should produce green tests on a clean clone.

Ready? [Start with chapter 1 — Hello World →](01-foundations/01-hello-world.md)
