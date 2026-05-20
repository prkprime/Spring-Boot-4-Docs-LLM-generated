# CD — Fly.io

Continuous delivery starts after CI has proved the repository is internally consistent. The next question is whether the tested application can run on a real platform with real runtime configuration.

This chapter uses Fly.io as the primary deploy target. The sample is still small, because the deployment mechanics are the lesson: port binding, Docker image build, health checks, environment variables, secrets, and a repeatable command.

The app uses Spring MVC and Actuator:

```xml
{% include-markdown "../../code/47-cd-flyio/maven/pom.xml" comments=false %}
```

## The Deployable App

The entry point is unchanged:

```java
{% include-markdown "../../code/47-cd-flyio/maven/src/main/java/dev/springboot4docs/ch_47_cd_flyio/Application.java" comments=false %}
```

The API exposes a tiny task list:

```java
{% include-markdown "../../code/47-cd-flyio/maven/src/main/java/dev/springboot4docs/ch_47_cd_flyio/TaskController.java" comments=false %}
```

The production behavior is in configuration:

```yaml
{% include-markdown "../../code/47-cd-flyio/maven/src/main/resources/application.yml" comments=false %}
```

`server.port: ${PORT:8080}` is the key setting. Many platforms provide the application port through an environment variable. If the app hard-codes a different port, the process can start but never receive traffic.

Readiness probes are enabled because the platform needs a reliable endpoint before routing traffic. A process can exist while the application is still starting, blocked on configuration, or not ready to serve requests.

## Dockerfile

Fly.io can build from a Dockerfile, so this chapter uses the same multi-stage shape as chapter 45:

```dockerfile
{% include-markdown "../../code/47-cd-flyio/maven/Dockerfile" comments=false %}
```

The first stage builds the jar with a JDK. The second stage runs the jar with a JRE as a non-root user. That image is the deployable artifact.

The `.dockerignore` file keeps local build output out of the image build context:

```text
{% include-markdown "../../code/47-cd-flyio/maven/.dockerignore" comments=false %}
```

## fly.toml

Fly.io reads deployment configuration from `fly.toml`:

```toml
{% include-markdown "../../code/47-cd-flyio/maven/fly.toml" comments=false %}
```

`app` is the Fly application name. It must be globally unique in your account context, so change `sb4-docs-tasks` before deploying your own copy.

`primary_region = "bom"` chooses Mumbai for this sample, matching the local user region. Pick the region closest to your users or your database. If your database is in another region, choose based on data latency, not your laptop.

The `[build]` section points to the Dockerfile. The `[env]` section contains non-secret runtime settings. Do not put passwords, tokens, database URLs with credentials, or private keys in `fly.toml`.

The `[http_service]` section tells Fly that the app listens on port 8080 inside the machine. `force_https = true` redirects plain HTTP to HTTPS. The readiness check calls `/actuator/health/readiness`, which maps directly to the Actuator probe enabled in `application.yml`.

## Secrets And Environment

Use `fly secrets set` for secret values:

```bash
fly secrets set DATABASE_URL='jdbc:postgresql://example.internal:5432/app'
fly secrets set DATABASE_USERNAME='app'
fly secrets set DATABASE_PASSWORD='change-me'
```

Those commands are examples. This chapter's app does not use a database yet, but the pattern is the same for OAuth client secrets, JWT issuer credentials, API tokens, and encryption keys.

Use `[env]` for values that are safe to commit and are useful documentation:

```toml
[env]
  SPRING_PROFILES_ACTIVE = "prod"
  SERVER_SHUTDOWN = "graceful"
```

Use platform secrets for values that must not be committed. The difference is not technical convenience; it is operational risk. A committed secret is copied to every clone, fork, cache, and backup.

## Local Verification

Before deploying, run tests:

=== "Maven"
    ```bash
    ./mvnw test
    ```

=== "Gradle"
    ```bash
    ./gradlew test
    ```

The tests call the API and the readiness endpoint:

```java
{% include-markdown "../../code/47-cd-flyio/maven/src/test/java/dev/springboot4docs/ch_47_cd_flyio/ApplicationTests.java" comments=false %}
```

Then build and run the same Docker image shape locally:

```bash
docker build -t sb4-docs/47-cd-flyio:dev .
docker run --rm -p 8080:8080 sb4-docs/47-cd-flyio:dev
```

Call the app:

```bash
curl -s localhost:8080/api/tasks
curl -s localhost:8080/actuator/health/readiness
```

This does not prove Fly.io will accept the deploy, but it proves the container starts and the HTTP surface behaves before you involve the remote platform.

## First Deploy

From the chapter directory, create the Fly app and deploy:

```bash
fly launch --no-deploy
fly deploy
```

If `fly launch` rewrites `fly.toml`, inspect the diff before deploying. The generated file may choose a different app name, region, or service shape than this chapter shows.

After deploy:

```bash
fly status
fly logs
fly open
```

Use `fly logs` when the process exits or readiness fails. Common first-deploy problems are wrong port binding, missing secrets, a container that exits immediately, or a health check path that does not match the app.

## GitHub Actions Deploy

CI and CD should be connected, but not mixed together blindly. The CI workflow from chapter 46 proves tests and docs. A deploy workflow should depend on the same checks or run the same test command before deployment.

A minimal deployment job has this shape:

```yaml
name: deploy-fly

on:
  push:
    branches: [main]
    paths:
      - "code/47-cd-flyio/**"
      - ".github/workflows/deploy-fly.yml"

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: superfly/flyctl-actions/setup-flyctl@master
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "25"
          cache: maven
      - name: test
        working-directory: code/47-cd-flyio/maven
        run: ./mvnw -q -B test
      - name: deploy
        working-directory: code/47-cd-flyio/maven
        run: flyctl deploy --remote-only
        env:
          FLY_API_TOKEN: ${{ secrets.FLY_API_TOKEN }}
```

Store `FLY_API_TOKEN` as a GitHub Actions secret. Do not commit it into the repository. The workflow runs tests, then deploys from the same directory that contains `fly.toml` and the Dockerfile.

## Rollback Thinking

Continuous delivery is not complete until rollback is boring. Before deploying a real service, know how to answer:

- Which image or release is currently running?
- Which release ran before it?
- How do you revert quickly?
- Does rollback require database rollback too?

For a stateless app like this chapter, rollback is usually an image release operation. For a database-backed app, schema migrations can make rollback harder. A backward-compatible migration strategy is part of production readiness, not an optional extra.

## What Part V Has Built

Part V started with profiles and configuration, then added logging, Actuator, observability, graceful shutdown, testing, virtual-thread tuning, Docker packaging, CI, and now CD. That is the production spine for the capstone.

The next major step is to combine the earlier concepts into one deployable Tasks API: REST, persistence, security, tests, image build, CI, and Fly.io deployment.
