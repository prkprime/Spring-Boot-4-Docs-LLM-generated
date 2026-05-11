# CI — GitHub Actions

Continuous integration is the habit of making a machine prove the project still works. A human can forget to run a chapter's tests. CI runs the same checks every time code changes.

This docs project has two kinds of checks:

- The documentation site must build strictly and render included source snippets correctly.
- Every chapter with a Maven sample must compile and pass tests.

The chapter's sample application is intentionally plain:

```xml
{% include-markdown "../../code/46-ci-github-actions/maven/pom.xml" comments=false %}
```

The CI lesson is not in this app's controller code. It is in the repository-level workflows that run every app's tests.

## Docs Workflow

The docs workflow builds MkDocs and deploys the site from `main`:

```yaml
{% include-markdown "../../.github/workflows/docs.yml" comments=false %}
```

The trigger is path-scoped. Changes under `docs/**`, `mkdocs.yml`, `.github/workflows/docs.yml`, or `code/**` run the workflow. `code/**` matters because chapter pages include snippets from real source files. A Java change can break a docs page even when no Markdown file changes.

The workflow checks out full history because the git revision date plugin needs repository metadata when it is enabled. In this local workspace that plugin is commented out until the repo is initialized correctly, but CI is already shaped for the final repository.

The build step runs:

```bash
python scripts/check-render.py --build
```

That script calls `mkdocs build --strict` and then scans the generated HTML. It catches a class of mistakes that a normal Markdown build can miss: raw include directives leaking into the page, included code failing to render as code, or Java source accidentally becoming paragraph text.

CI should fail on broken docs. A tutorial site is product code. If a page links to a missing chapter or renders source code as prose, the reader experiences it as a bug.

## Code Workflow

The code workflow discovers every chapter and runs its tests:

```yaml
{% include-markdown "../../.github/workflows/code.yml" comments=false %}
```

The `discover` job emits a JSON matrix of chapter slugs. It looks for directories under `code/` that contain `maven/`. That keeps the workflow from hard-coding the chapter list. When chapter 48 is added later, CI picks it up automatically.

The `build` job runs with a matrix:

```yaml
strategy:
  fail-fast: false
  matrix:
    chapter: ${{ fromJson(needs.discover.outputs.chapters) }}
```

`fail-fast: false` is important for a docs project with many independent samples. If chapter 18 fails, you still want to know whether chapter 25 and chapter 43 fail too. Otherwise you fix one problem, push again, and discover the next failure later.

Each matrix leg sets up Java 25:

```yaml
- uses: actions/setup-java@v4
  with:
    distribution: temurin
    java-version: "25"
    cache: maven
```

That matches the course recommendation. Spring Boot 4 can run on Java 17+, but this guide's code targets Java 25 so readers learn the current LTS path.

## Reusing Local Scripts

The workflow does not duplicate test logic in YAML. It calls the same scripts you can run locally:

```bash
scripts/parity-check.sh ${{ matrix.chapter }}
scripts/verify-chapter.sh ${{ matrix.chapter }}
```

`verify-chapter.sh` runs Maven tests and, when a `gradle/` project exists, Gradle tests too:

```bash
{% include-markdown "../../scripts/verify-chapter.sh" comments=false %}
```

`parity-check.sh` compares Maven and Gradle source trees when both build-tool variants exist:

```bash
{% include-markdown "../../scripts/parity-check.sh" comments=false %}
```

The current project is Maven-only because Spring Initializr's Gradle generation was failing when these chapters were scaffolded. The script handles that by skipping parity when `gradle/` is absent. When Gradle samples are backfilled, the same CI workflow starts enforcing parity without a workflow rewrite.

This is a useful pattern: put real logic in scripts, and keep YAML as orchestration. Scripts can be tested locally, reviewed normally, and run by other CI systems if the repository moves later.

## What The CI Proves

For every chapter, CI proves:

- The Maven wrapper works on a clean machine.
- Dependencies resolve from public repositories.
- The code compiles on Java 25.
- Tests pass without local IDE state.
- The docs still render after source snippets change.

That is not the same as proving the production system is healthy. CI does not prove a deployment target can boot the image, secrets are present, DNS is correct, or a database migration is safe under production load. CI proves the repository is internally consistent.

That boundary matters. If CI becomes a fake production environment, it grows slow and fragile. Keep CI fast enough that developers trust it, then add deployment checks after the artifact is built.

## Pull Request Shape

A practical pull request pipeline should separate three questions:

1. Does the docs site build?
2. Do the code samples test?
3. If this lands on `main`, should it deploy?

The workflows here answer the first two questions on pull requests. Deployment is reserved for pushes to `main` in the docs workflow. Chapter 47 applies the same idea to application deployment.

For a real repository, add branch protection so `main` requires the docs workflow and code workflow to pass before merge. Without that rule, CI becomes advisory. With that rule, the repository refuses to accept broken docs or broken samples.

## Caching

The code workflow enables Maven caching through `actions/setup-java`. The docs workflow enables pip caching through `actions/setup-python`. Caches reduce repeated dependency downloads, but they are an optimization. The build should still work from an empty cache.

Do not store build outputs such as `target/` in the repository to make CI faster. Let the build produce them. Commit source, configuration, scripts, and tests. Generate outputs in CI.

## Local Dry Run

Before pushing, run the same commands locally:

```bash
scripts/verify-chapter.sh 46-ci-github-actions
python scripts/check-render.py --build
```

If those pass locally, CI still can fail because the CI machine is cleaner. That is useful feedback. It means the local machine had state the project did not declare.

Chapter 47 takes the tested artifact and deploys it to Fly.io.
