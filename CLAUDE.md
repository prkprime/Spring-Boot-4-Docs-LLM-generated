# CLAUDE.md

This file is for AI coding agents working in this repository.

## Repository Goal

Build and maintain a comprehensive Spring Boot 4 follow-along documentation site. The guide must use Spring Boot 4 / Spring Framework 7 idioms only, with runnable Maven examples and beginner-friendly explanations.

## Current Shape

- Docs live in `docs/` and are rendered by MkDocs Material.
- Chapter code lives in `code/<chapter>/maven` (Maven) and `code/<chapter>/gradle` (Gradle).
- Docs include code snippets from the chapter projects with `include-markdown`.
- Gradle environments are fully supported and automatically synchronized. Codebase-wide IntelliJ formatting rules (4-space tabs for Java/XML, 2-space tabs for YAML/Markdown) are enforced by Spotless.

## Hard Rules

- Keep examples Spring Boot 4 only.
- Use `spring-boot-starter-webmvc` and `spring-boot-starter-webmvc-test` for servlet MVC examples.
- Use Jakarta APIs (`jakarta.*`), not `javax.*`.
- Use `@MockitoBean` for Spring tests, not deprecated `@MockBean`.
- Do not add blanket `hl_lines` or `linenums` attributes to code fences. MkDocs highlighting support is configured globally.
- Keep source snippets runnable. If docs show code from `code/`, the referenced chapter should pass tests.
- Code formatting follows IntelliJ IDEA defaults (4-space indent for Java/XML, 2-space for YAML/Markdown/JSON), enforced by the Spotless plugin (`spotless:apply` or `./gradlew spotlessApply`).
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
