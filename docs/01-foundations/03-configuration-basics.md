# Chapter 3: Configuration Basics

Most applications need values that change from one environment to another. A
developer laptop might talk to an in-memory database, while production talks to
a managed database. Local logging might be verbose, while production logging is
more careful. A development app might call a sandbox API, while production calls
the real provider.

Those values should not require a code change.

That is the basic idea behind externalized configuration. The behavior of the
application is still described by code, but the values that vary by environment
live outside the Java classes. Operations teams can change deployment settings
without asking a developer to recompile the application. You can run the same
jar in different environments and give it different configuration at startup.
And, when handled correctly, secrets such as passwords and API tokens can come
from the runtime environment instead of being committed to source control.

In this chapter, the application is intentionally small: it exposes one greeting
endpoint whose text comes from configuration. That gives us enough room to cover
the important Spring Boot configuration pieces without mixing in database,
security, or cloud deployment details too early.

## The Configuration Files

Spring Boot can read both `application.properties` and `application.yml`.
They have the same capability. You can express the same configuration in either
format, and Spring Boot will bind both into the same `Environment`.

This guide uses YAML.

YAML is not more powerful than properties files, but it is often easier to read
when values are naturally nested. In this chapter, the greeting settings belong
together under one prefix, so the YAML shape makes that grouping obvious.

Here is the base configuration:

```yaml
{% include-markdown "../../code/03-configuration-basics/maven/src/main/resources/application.yml" comments=false %}
```

The important thing is the prefix. The greeting values live under one logical
configuration namespace. Later, our Java record will bind to that namespace with
`@ConfigurationProperties`.

The project also has a production-specific configuration file:

```yaml
{% include-markdown "../../code/03-configuration-basics/maven/src/main/resources/application-prod.yml" comments=false %}
```

`application.yml` is the base file. It applies unless a higher-priority source
replaces one of its values. `application-prod.yml` is a profile-specific file.
Spring Boot loads it only when the `prod` profile is active.

!!! note "Spring Boot 4"
    The configuration model you use here is the same day-to-day model you will
    use in real Spring Boot 4 applications: base configuration, profile-specific
    overrides, and type-safe binding with `@ConfigurationProperties`.

## Precedence In Practice

Spring Boot combines configuration from many places. The full list is long, but
you do not need to memorize it right now. For this chapter, keep this practical
order in mind, from highest priority to lowest:

1. Command-line arguments
2. Environment variables
3. Profile-specific YAML, such as `application-prod.yml`
4. Default YAML, such as `application.yml`

Higher-priority values override lower-priority values.

That means the value in `application.yml` is the default. If the `prod` profile
is active and `application-prod.yml` defines the same property, the production
value wins. If you pass the property as a command-line argument, the command-line
value wins over both files.

This is why externalized configuration is useful. You can ship the same
application artifact everywhere and let each environment supply the values it
needs.

## Profiles

Profiles are named sets of configuration.

In this project, the base file is always `application.yml`. The production file
is named `application-prod.yml`, where `prod` is the profile name. Spring Boot
recognizes that naming convention automatically.

When no profile is active, the application uses the base values:

=== "Maven"
    === "Maven"
    ```bash
    ./mvnw spring-boot:run
    ```

=== "Gradle"
    ```bash
    ./gradlew bootRun
    ```

=== "Gradle"
    ```bash
    ./gradlew bootRun
    ```

When the `prod` profile is active, Spring Boot also loads
`application-prod.yml`:

=== "Maven"
    ```bash
    ./mvnw spring-boot:run -Dspring-boot.run.arguments=--spring.profiles.active=prod
    ```

=== "Gradle"
    ```bash
    ./gradlew bootRun --args='--spring.profiles.active=prod'
    ```

If you run the jar directly, the same idea applies:

=== "Maven"
    ```bash
    java -jar target/03-configuration-basics-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
    ```

=== "Gradle"
    ```bash
    java -jar build/libs/03-configuration-basics-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
    ```

The profile is activated by the standard Spring Boot property
`spring.profiles.active`. In this example, the value is `prod`.

!!! warning "Do not commit real secrets"
    This sample has a committed `application-prod.yml` because it is teaching
    profile overrides. In a real application, do not commit production secrets
    to that file. Later chapters will cover environment variables and secret
    stores such as Vault.

## The Application Class

The main application class does two jobs in this chapter. It starts the Spring
Boot application, and it enables scanning for configuration properties classes.

```java
{% include-markdown "../../code/03-configuration-basics/maven/src/main/java/dev/springboot4docs/ch_03_configuration_basics/Application.java" comments=false %}
```

The familiar `@SpringBootApplication` annotation marks this as the entry point
for the application.

The configuration-specific part is `@ConfigurationPropertiesScan`. This tells
Spring Boot to look for classes annotated with `@ConfigurationProperties` and
register them as beans.

That registration step matters. A configuration properties class is not just a
plain data holder sitting on the classpath. It needs to be discovered, bound,
and validated by Spring Boot during startup. Once it is registered as a bean,
other components can inject it through their constructors.

!!! note "Spring Boot 4"
    Prefer `@ConfigurationPropertiesScan` on your application class when you
    have your own configuration properties types. It keeps the configuration
    classes focused on describing settings instead of also needing component
    stereotypes.

## Type-Safe Configuration With `@ConfigurationProperties`

Here is the configuration properties type used by the greeting endpoint:

```java
{% include-markdown "../../code/03-configuration-basics/maven/src/main/java/dev/springboot4docs/ch_03_configuration_basics/GreetingProperties.java" comments=false %}
```

`@ConfigurationProperties` tells Spring Boot which property prefix this type
binds to. The fields in the record correspond to the nested values in
`application.yml`.

This gives you type-safe configuration. Instead of looking up strings manually
from the environment, the rest of the application receives a strongly typed
object. If a value should be a number, it can be modeled as a number. If a value
is required, it can be validated. If a name changes, the compiler helps you find
the places that use it.

The record style is the modern approach. The configuration object is immutable:
there are no setters, and the values are provided through the canonical record
constructor. Spring Boot binds the external properties into that constructor
when the application starts.

Validation also happens at startup. That is a major advantage over reading
configuration values manually inside request-handling code. A missing or invalid
configuration value fails fast when the application boots, before the first
request arrives.

For a small tutorial endpoint, that may feel like extra structure. In a real
service, it prevents a common production problem: the app starts, looks healthy,
and then fails only when a rarely used code path tries to read a bad setting.

## The Controller

The controller shows the payoff. It does not know where the values came from.
It only depends on the typed `GreetingProperties` bean.

```java
{% include-markdown "../../code/03-configuration-basics/maven/src/main/java/dev/springboot4docs/ch_03_configuration_basics/GreetingController.java" comments=false %}
```

Read the controller from top to bottom.

The class is a web controller, so Spring MVC can route HTTP requests to it. The
constructor receives `GreetingProperties`. That dependency is available because
the main application class enabled configuration properties scanning.

The request-handling method maps the greeting endpoint. When a request arrives,
the controller reads the configured greeting values from the `GreetingProperties`
record and builds the response.

Notice what the controller does not do. It does not open `application.yml`.
It does not ask Spring for raw strings by property name. It does not know whether
the active value came from the base YAML file, the production YAML file, an
environment variable, or a command-line argument.

That separation is the point.

Spring Boot resolves the configuration. `GreetingProperties` gives the
application a typed view of it. The controller uses that typed view to handle
the request.

## Testing The Default Configuration

The first test exercises the endpoint with the default configuration.

```java
{% include-markdown "../../code/03-configuration-basics/maven/src/test/java/dev/springboot4docs/ch_03_configuration_basics/GreetingControllerTest.java" comments=false %}
```

The important testing detail is that `@ConfigurationProperties` beans need a
little more setup than a very simple `@WebMvcTest`.

A plain web-slice test focuses on MVC components. That is useful because it
keeps tests fast and narrow, but the slice does not automatically behave like a
full application startup. If the controller needs a configuration properties
bean, the test must make that bean available in the slice.

This test does that explicitly. It keeps the test focused on the web endpoint
while still giving the controller the same kind of typed configuration object it
uses at runtime.

Once the test context is assembled, the test performs a request against the
greeting endpoint and asserts the response produced from the default YAML
values.

The lesson is not just "write a controller test." The lesson is that
configuration is part of your application contract. If a controller depends on
configuration, the test should make that dependency visible.

## Testing The Production Profile

The second test activates the production profile and verifies that the
profile-specific values win.

```java
{% include-markdown "../../code/03-configuration-basics/maven/src/test/java/dev/springboot4docs/ch_03_configuration_basics/GreetingControllerProdProfileTest.java" comments=false %}
```

This test is the same endpoint, but with a different active profile. With
`prod` active, Spring Boot loads `application-prod.yml` in addition to the base
`application.yml`.

When both files define the same greeting property, the value from
`application-prod.yml` overrides the base value. The test proves that behavior
at the HTTP boundary: it calls the controller and checks the response that a
client would see.

That is a useful style for configuration tests. You are not just testing that a
file exists. You are testing that the application behaves differently when a
profile-specific configuration source is active.

It also reinforces the earlier testing detail. Because the controller depends on
a `@ConfigurationProperties` bean, the test setup must include the pieces needed
to bind that bean. In a full `@SpringBootTest`, the whole application context
would handle that. In a narrower MVC slice, you make the needed configuration
properties support explicit.

## Run It

Start the application with the default profile:

=== "Maven"
    === "Maven"
    ```bash
    ./mvnw spring-boot:run
    ```

=== "Gradle"
    ```bash
    ./gradlew bootRun
    ```

=== "Gradle"
    ```bash
    ./gradlew bootRun
    ```

Then call the endpoint:

```bash
curl localhost:8080/greeting
```

Now stop the app and start it with the `prod` profile:

=== "Maven"
    ```bash
    ./mvnw spring-boot:run -Dspring-boot.run.arguments=--spring.profiles.active=prod
    ```

=== "Gradle"
    ```bash
    ./gradlew bootRun --args='--spring.profiles.active=prod'
    ```

Call the same endpoint again:

```bash
curl localhost:8080/greeting
```

The route is the same. The code is the same. The active configuration is
different.

| Default profile | `prod` profile |
| --- | --- |
| Uses values from `application.yml` | Uses values from `application.yml`, then overrides matching values from `application-prod.yml` |
| `curl localhost:8080/greeting` | `curl localhost:8080/greeting` |
| Response contains the default greeting text | Response contains the production greeting text |

If the controller also accepts a request parameter for the name, try that with
both profiles too:

```bash
curl "localhost:8080/greeting?name=Boot"
```

The configured greeting text still comes from the active configuration, while
the request-specific name comes from the HTTP request.

## What You Should Take Away

Externalized configuration lets the same application run in different
environments without code changes. Spring Boot reads configuration from many
places and applies a clear precedence order, so command-line arguments can
override environment variables, environment variables can override YAML, and
profile-specific YAML can override base YAML.

Use either `application.properties` or `application.yml`; they are equivalent in
capability. This guide uses YAML because nested configuration is easier to read
in this shape.

Profiles let you keep a base configuration in `application.yml` and override
environment-specific values in files such as `application-prod.yml`. Activate
the production profile with `--spring.profiles.active=prod`.

For application code, prefer `@ConfigurationProperties` over scattered string
lookups. Pair it with `@ConfigurationPropertiesScan`, use records for immutable
constructor binding, and add validation so invalid configuration fails during
startup.

That gives you a clean path from external values to application behavior:

1. YAML defines the values.
2. Spring Boot resolves the active configuration.
3. `GreetingProperties` binds and validates the values.
4. `GreetingController` uses the typed configuration bean.
5. Tests verify both the default and profile-specific behavior.
