# Profiles & Externalized Config

Chapter 3 introduced `application.yml`, profile-specific files, and type-safe configuration. This chapter goes deeper into the production model: where configuration can come from, how profiles compose, how environment variables bind, how secrets should be supplied, and how file-mounted secrets become properties through config trees.

The sample application stays small on purpose. It exposes `/greet`, which returns the active greeting, and `/info`, which returns the bound `AppProperties` record. That gives us a visible way to prove which profile and property source won without mixing in database or deployment code.

The same pattern scales to larger services: keep the code stable, and let each runtime supply the values it owns.

## The Precedence Ladder

Spring Boot builds one `Environment` from many property sources. When the same key appears in more than one place, the higher-priority source wins. For this chapter, use this high-to-low ladder:

1. Command-line arguments, such as `--app.greeting=Hello`.
2. `SPRING_APPLICATION_JSON`.
3. JNDI properties.
4. Operating-system environment variables.
5. Java system properties.
6. Config tree files, such as volume-mounted secrets.
7. Profile-specific YAML, such as `application-prod.yml`.
8. Default YAML, such as `application.yml`.
9. `@PropertySource` declarations.
10. Default properties set programmatically.

You do not need to memorize every rung, but you do need the rule: external sources override packaged defaults. That is why the same jar can run locally, in test, and in production without recompilation.

Use the ladder deliberately. Packaged YAML is good for defaults that describe the application: a feature flag default, a timeout that is safe everywhere, a logging category that helps local development. Environment variables are better for deployment-owned values: URLs, credentials, region names, queue names, and anything an operator expects to change without rebuilding the artifact. Command-line arguments are best for one-off runs, experiments, and temporary overrides because they sit at the top of the ladder and are visible in the launch command.

`SPRING_APPLICATION_JSON` is useful when a platform gives you one environment variable but you need to supply several structured values:

```bash
SPRING_APPLICATION_JSON='{"app":{"greeting":"Hello from JSON"}}' ./mvnw spring-boot:run
```

It should be used sparingly. It is powerful, but it can hide a lot of configuration inside a single opaque string. Prefer ordinary environment variables or mounted files when the deployment platform supports them cleanly.

`@PropertySource` appears low in the list because it is added after the application context is being prepared. It is still useful for a few framework-level cases, but it should not be your main application configuration strategy in Spring Boot. The Boot-native config data system, meaning `application.yml`, profile files, imports, environment variables, and config trees, is the model to reach for first.

## Dependencies

The application uses Spring MVC, the Spring Boot web test starter, and validation support for configuration properties.

```xml
{% include-markdown "../../code/37-profiles-config/maven/pom.xml" comments=false %}
```

Validation is not decoration here. When configuration is required, the app should fail during startup rather than accepting traffic with a missing database URL or blank service name.

## Base Configuration

The base file applies in every environment unless a higher-priority source replaces one of its keys.

```yaml
{% include-markdown "../../code/37-profiles-config/maven/src/main/resources/application.yml" comments=false %}
```

The `app.*` keys are the application-owned settings. `app.secret-api-key` uses a placeholder:

```text
${SECRET_API_KEY:default-key}
```

That means: read `SECRET_API_KEY` from the environment if it exists; otherwise use `default-key`. The default is convenient for a tutorial and for local development. In a real production deployment, a secret should come from the runtime environment or a mounted secret file, not from source control.

The file also defines a profile group:

```text
spring.profiles.group.local: dev,debug
```

Activating `local` activates both `dev` and `debug`. Groups are useful when a human-friendly mode really means several technical profiles. A local developer may want development defaults plus debug logging. A staging environment may want production-like database settings plus extra diagnostics.

The second YAML document is separated by `---` and guarded by `spring.config.activate.on-profile: debug`. Multi-document YAML lets one physical file contain sections that only apply under specific profiles. Keep it modest; if a profile section grows large, a separate `application-{profile}.yml` is easier to scan.

## Profile Files

Spring Boot automatically recognizes the `application-{profile}.yml` convention. If the `dev` profile is active, it loads `application-dev.yml` in addition to the base file.

```yaml
{% include-markdown "../../code/37-profiles-config/maven/src/main/resources/application-dev.yml" comments=false %}
```

The development profile changes the greeting and makes selected logging more chatty. This is the kind of override that belongs in a profile file: useful locally, noisy or risky elsewhere.

The production profile is stricter:

```yaml
{% include-markdown "../../code/37-profiles-config/maven/src/main/resources/application-prod.yml" comments=false %}
```

Notice `database-url: ${DB_URL}`. There is no committed fallback. If production starts without `DB_URL`, startup fails instead of silently using a fake database location.

Tests get their own profile too:

```yaml
{% include-markdown "../../code/37-profiles-config/maven/src/main/resources/application-test.yml" comments=false %}
```

The test file makes assertions deterministic. It can provide fake secrets and in-memory URLs because tests are not a production runtime.

## Activating Profiles

There are three everyday ways to activate a profile.

From the command line:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--spring.profiles.active=prod
```

From the environment:

```bash
SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run
```

From tests:

```java
@ActiveProfiles("test")
```

Profile activation itself is just configuration. `SPRING_PROFILES_ACTIVE=prod` is the environment-variable form of `spring.profiles.active=prod`. Spring Boot's relaxed binding maps environment variable names such as `SPRING_PROFILES_ACTIVE`, `SECRET_API_KEY`, and `APP_SECRET_API_KEY` into property-style names by converting uppercase underscore-separated names into dotted or dashed property names where appropriate.

Relaxed binding is why these shapes can all participate in the same configuration model:

```text
app.secret-api-key
APP_SECRET_API_KEY
app.secretApiKey
```

Use canonical kebab-case in YAML. Use uppercase underscore names in shell environments.

## Type-Safe Properties

The main application class enables scanning for `@ConfigurationProperties` types.

```java
{% include-markdown "../../code/37-profiles-config/maven/src/main/java/dev/springboot4docs/ch_37_profiles_config/Application.java" comments=false %}
```

The properties record binds the `app` prefix.

```java
{% include-markdown "../../code/37-profiles-config/maven/src/main/java/dev/springboot4docs/ch_37_profiles_config/AppProperties.java" comments=false %}
```

Records are a good fit for configuration. They are immutable, concise, and constructor-bound. Spring Boot creates the record from the resolved environment after all the property sources and profiles have been applied.

`@Validated` turns Jakarta Bean Validation constraints into startup checks. The `@NotBlank` annotations mean the application requires each value to be present and non-empty. This is especially useful for values like `databaseUrl`, where an accidental blank string is not better than a missing property.

The controller uses the typed record instead of reading raw strings from the environment.

```java
{% include-markdown "../../code/37-profiles-config/maven/src/main/java/dev/springboot4docs/ch_37_profiles_config/GreetingController.java" comments=false %}
```

`/greet` makes profile changes obvious. `/info` returns the whole record so you can see the final values. Do not expose secrets this way in a real service; this endpoint is intentionally transparent so the chapter can show binding behavior.

## Secrets And Config Trees

The safest default is simple: do not commit secrets. Put harmless defaults in source control when they are genuinely harmless, and require real production secrets from the deployment environment.

Environment variables are the classic twelve-factor answer:

```bash
SECRET_API_KEY=hunter2 DB_URL=jdbc:postgresql://localhost:5432/app ./mvnw spring-boot:run -Dspring-boot.run.arguments=--spring.profiles.active=prod
```

That command sets two environment variables for the process, starts the app with the `prod` profile, and lets the production YAML resolve `${DB_URL}` while the base YAML resolves `${SECRET_API_KEY:default-key}`.

Config trees are the file-mounted version of the same idea. Instead of passing one giant configuration file, a platform such as Kubernetes or Docker can mount a directory where each filename is a property name and each file's content is the value.

For a directory like this:

```text
/run/secrets/
  app.secret-api-key
  app.database-url
```

you can import it with:

```yaml
spring:
  config:
    import: configtree:/run/secrets/
```

Spring Boot then treats `/run/secrets/app.secret-api-key` as the value for `app.secret-api-key`, and `/run/secrets/app.database-url` as the value for `app.database-url`. This pattern has existed since Spring Boot 2.4, but it is worth reintroducing in a production chapter because it maps cleanly to container secret volumes. The application still reads normal properties; the platform decides how the secret files appear.

## Tests

The tests start the full application with different active profiles and call the API through `MockMvcTester`.

```java
{% include-markdown "../../code/37-profiles-config/maven/src/test/java/dev/springboot4docs/ch_37_profiles_config/TestProfileApplicationTests.java" comments=false %}
```

`@ActiveProfiles("test")` loads the base YAML and `application-test.yml`. The greeting assertion proves the profile-specific value wins over the base value. The `/info` assertion proves that the test profile can override the default secret value with a harmless test value.

The production test uses a different profile and supplies `DB_URL` as a test property so the production placeholder can resolve.

```java
{% include-markdown "../../code/37-profiles-config/maven/src/test/java/dev/springboot4docs/ch_37_profiles_config/ProdProfileApplicationTests.java" comments=false %}
```

This test is deliberately close to the production contract: `prod` expects the environment to provide a database URL. The test provides one and then verifies that the bound record sees it.

Run the chapter from its Maven directory:

```bash
./mvnw -q -B test
```

The important result is not just that the context loads. The tests prove that two different profiles produce two different runtime configurations from the same application code.

## What To Keep

Use `application.yml` for defaults that are safe everywhere. Use `application-{profile}.yml` for environment-specific defaults. Use profile groups when one named mode should activate several profiles together. Use environment variables or config trees for secrets and deployment-owned values. Bind your own settings with `@ConfigurationProperties`, scan them with `@ConfigurationPropertiesScan`, and validate them so bad configuration fails fast.

Chapter 38 builds on this production foundation by making the application logs structured enough for real operations work.
