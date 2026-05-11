# Spring Boot 4 Follow-Along

This repository is a Spring Boot 4 documentation site plus runnable chapter code. It is written as a backend-only, follow-along guide for people who know Java basics and HTTP basics, but want current Spring Boot 4 / Spring Framework 7 examples instead of upgraded Spring Boot 2 or 3 material.

The docs are built with MkDocs Material. Every code chapter lives under `code/<chapter>/maven`, and the docs include source snippets directly from those projects so the guide and the tested code stay aligned.

## Contents

- `docs/` - MkDocs pages, organized from Hello World through web, data, security, production, capstone, mini-projects, and appendices.
- `code/` - standalone Maven Spring Boot 4 projects for each runnable chapter.
- `scripts/verify-chapter.sh` - runs the tests for one chapter.
- `scripts/check-render.py` - strict MkDocs build plus rendered-snippet checks.
- `scripts/verify-all.sh` - full local verification for docs and all chapter projects.
- `.github/workflows/` - GitHub Actions for docs publishing and chapter test matrices.

## Requirements

- Java 25 or newer.
- Docker, for chapters that use Testcontainers.
- Python 3.13 or a compatible Python 3.x runtime for MkDocs.
- Bash-compatible shell.

The code samples currently use Maven projects. Gradle generation is intentionally disabled until the upstream Initializr Gradle issue noted in `scripts/new-chapter.sh` is resolved.

## Local Setup

Create the docs environment:

```bash
python3 -m venv .venv
. .venv/bin/activate
python -m pip install --upgrade pip
pip install -r requirements-docs.txt
```

Install the repository Git hooks after cloning:

```bash
scripts/install-hooks.sh
```

## Run The Docs

Build and validate all rendered pages:

```bash
python3 scripts/check-render.py --build
```

Serve the site locally:

```bash
.venv/bin/mkdocs serve
```

Then open the local URL printed by MkDocs.

## Test The Code

Run one chapter:

```bash
scripts/verify-chapter.sh 25-caching-redis
```

Run every chapter and the docs render check:

```bash
scripts/verify-all.sh
```

Some chapters start PostgreSQL, Redis, Keycloak, LDAP, or other containers through Testcontainers. Keep Docker running before the full sweep.

## CI And Publishing

The repository includes two GitHub Actions workflows:

- `code` discovers every chapter under `code/*/maven` and runs the chapter verifier as a matrix.
- `docs` installs the pinned MkDocs stack, runs the strict render check, uploads the `site/` artifact, and deploys to GitHub Pages on pushes to `main`.

To publish the docs from GitHub:

1. Push the repository to GitHub.
2. In the repository settings, enable GitHub Pages with GitHub Actions as the source.
3. Push to `main`; the `docs` workflow will deploy the built site.

Update `site_url` and `repo_url` in `mkdocs.yml` after the final GitHub repository URL is known.

## Contributor Notes

- Keep examples Spring Boot 4 only. Prefer `spring-boot-starter-webmvc` and `spring-boot-starter-webmvc-test` for servlet MVC chapters.
- Use Jakarta imports, not `javax.*`.
- Use `@MockitoBean` instead of deprecated `@MockBean` examples.
- Keep generated files out of Git: `site/`, `.venv/`, Maven `target/`, Gradle `build/`, and local caches are ignored.
- When changing a chapter, run that chapter verifier and the docs render check before committing.
