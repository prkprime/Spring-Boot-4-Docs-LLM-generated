# CLAUDE.md

This file is for AI coding agents working in this repository.

## Repository Goal

Build and maintain a comprehensive Spring Boot 4 follow-along documentation site. The guide must use Spring Boot 4 / Spring Framework 7 idioms only, with runnable Maven examples and beginner-friendly explanations.

## Current Shape

- Docs live in `docs/` and are rendered by MkDocs Material.
- Chapter code lives in `code/<chapter>/maven`.
- Docs include code snippets from the chapter projects with `include-markdown`.
- Gradle samples are currently absent because Initializr Gradle generation was failing upstream. Do not invent partial Gradle trees unless that issue is intentionally revisited.

## Hard Rules

- Keep examples Spring Boot 4 only.
- Use `spring-boot-starter-webmvc` and `spring-boot-starter-webmvc-test` for servlet MVC examples.
- Use Jakarta APIs (`jakarta.*`), not `javax.*`.
- Use `@MockitoBean` for Spring tests, not deprecated `@MockBean`.
- Do not add blanket `hl_lines` or `linenums` attributes to code fences. MkDocs highlighting support is configured globally.
- Keep source snippets runnable. If docs show code from `code/`, the referenced chapter should pass tests.
- Do not commit build outputs, local environments, or caches.

## Verification Commands

Run one chapter:

```bash
scripts/verify-chapter.sh 25-caching-redis
```

Run docs render checks:

```bash
python3 scripts/check-render.py --build
```

Run the full repository verification:

```bash
scripts/verify-all.sh
```

Docker must be running for Testcontainers chapters.

## Editing Guidance

- Prefer small, chapter-scoped edits.
- When changing a code sample, update the matching docs in the same turn.
- When changing docs, run `python3 scripts/check-render.py --build`.
- When changing shared testing, security, data, or build conventions, run the affected chapters and consider `scripts/verify-all.sh`.
- Keep prose direct and beginner-friendly. Explain why the Spring Boot 4 choice matters when older guides differ.
