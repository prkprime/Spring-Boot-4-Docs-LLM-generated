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

## CI/CD Pipeline Workflow

The repository uses a unified GitHub Actions pipeline to run all code verification checks and build the documentation site in a single workflow. Deployment to GitHub Pages only occurs when the verification job succeeds on `main`:

```yaml
{% include-markdown "../../.github/workflows/pipeline.yml" comments=false %}
```

The trigger is path-scoped and executes on any push or pull request. The workflow does two main tasks:

1. **Verify Code and Build Docs (`verify`)**:
   - Sets up JDK 25 with Maven caching.
   - Sets up Gradle with caching via `gradle/actions/setup-gradle@v4`.
   - Sets up Python 3.13 with pip cache for MkDocs.
   - Installs documentation dependencies and executes `scripts/verify-all.sh`.
   - Compiles the documentation site and checks for broken links.
   - Loops through all 48 code chapters to run Spotless, parity checks, and test suites.
   - Uploads the compiled `site` artifact if the branch is `main`.

2. **Deploy Docs to Pages (`deploy`)**:
   - Runs only on pushes or manual dispatches to the `main` branch.
   - Deploys the built `site` artifact to GitHub Pages via `actions/deploy-pages`.

This guarantees that the site is **never** deployed if any code sample fails compilation or testing, or if there is a broken markdown link.

The verification step runs:

```bash
python scripts/check-render.py --build
```

That script calls `mkdocs build --strict` and then scans the generated HTML. It catches a class of mistakes that a normal Markdown build can miss: raw include directives leaking into the page, included code failing to render as code, or Java source accidentally becoming paragraph text.

CI should fail on broken docs. A tutorial site is product code. If a page links to a missing chapter or renders source code as prose, the reader experiences it as a bug.

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

The unified pipeline answers the first two questions on pull requests (running the `verify` job). Deployment is reserved for pushes to `main` in the `deploy` job. Chapter 47 applies the same idea to application deployment.

For a real repository, add branch protection so `main` requires the `pipeline` workflow to pass before merge. Without that rule, CI becomes advisory. With that rule, the repository refuses to accept broken docs or broken samples.

## Caching

The pipeline enables Maven caching through `actions/setup-java`, Gradle caching through `gradle/actions/setup-gradle`, and pip caching through `actions/setup-python`. Caches reduce repeated dependency downloads, but they are an optimization. The build should still work from an empty cache.

Do not store build outputs such as `target/` in the repository to make CI faster. Let the build produce them. Commit source, configuration, scripts, and tests. Generate outputs in CI.

## Local Dry Run

Before pushing, run the same commands locally:

```bash
scripts/verify-chapter.sh 46-ci-github-actions
python scripts/check-render.py --build
```

If those pass locally, CI still can fail because the CI machine is cleaner. That is useful feedback. It means the local machine had state the project did not declare.

Chapter 47 takes the tested artifact and deploys it to Fly.io.
