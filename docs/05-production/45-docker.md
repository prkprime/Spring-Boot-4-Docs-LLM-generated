# Docker

By this point the application can be configured, observed, tested, and tuned. The next production step is packaging. A container image gives the same runnable unit to your laptop, CI, staging, and production. The goal is not "Docker because everyone uses Docker"; the goal is a repeatable process that turns source code into one immutable artifact.

This chapter packages a small Spring MVC application with Actuator health probes:

```xml
{% include-markdown "../../code/45-docker/maven/pom.xml" comments=false %}
```

The dependencies are deliberately ordinary. `spring-boot-starter-webmvc` gives us the HTTP API. `spring-boot-starter-actuator` gives health endpoints that a container platform can call. The Maven plugin creates the executable jar that the image will run.

## A Small Container-Friendly App

The application entry point is still just a normal Boot class:

```java
{% include-markdown "../../code/45-docker/maven/src/main/java/dev/springboot4docs/ch_45_docker/Application.java" comments=false %}
```

The sample endpoint returns build/runtime data:

```java
{% include-markdown "../../code/45-docker/maven/src/main/java/dev/springboot4docs/ch_45_docker/BuildController.java" comments=false %}
```

This endpoint is not a replacement for `/actuator/info`. It is here so you can prove that the packaged application is the same code you tested. When you run the container and call `/api/build`, you should see the application name and Java feature version from the running JVM.

The container runtime usually provides the port through an environment variable, so the app reads `PORT` with a default:

```yaml
{% include-markdown "../../code/45-docker/maven/src/main/resources/application.yml" comments=false %}
```

`server.port: ${PORT:8080}` means "use `PORT` when it exists, otherwise use 8080." That is a common pattern for platforms that inject a port at runtime. `server.shutdown: graceful` keeps the behavior from chapter 41 when the container receives a stop signal.

The health probe configuration exposes the normal health endpoint plus liveness and readiness groups. A container orchestrator should not guess whether your app is ready by checking if the process exists. It should call an application endpoint that knows whether the app is ready to receive traffic.

## Build The Jar First

Before Docker enters the picture, prove the application builds:

```bash
./mvnw -q -B test
./mvnw -q -B package
```

The first command runs the tests. The second creates the executable jar in `target/`. Spring Boot's Maven plugin repackages the jar so it can be started with:

```bash
java -jar target/45-docker-0.0.1-SNAPSHOT.jar
```

That direct jar command is worth understanding even when you deploy containers. Docker does not change what a Spring Boot app is. The final image still starts a JVM and runs an executable jar.

## Multi-Stage Dockerfile

Here is the Dockerfile:

```dockerfile
{% include-markdown "../../code/45-docker/maven/Dockerfile" comments=false %}
```

This is a multi-stage build. The first stage uses a JDK because Maven needs a compiler. It copies the wrapper and `pom.xml`, downloads dependencies, then copies `src` and builds the jar.

The second stage uses a JRE because the running application does not need the compiler or Maven. It creates a non-root user, copies the jar from the build stage, exposes port 8080, and starts the app with `java -jar`.

The dependency step is separated from the source copy on purpose:

```dockerfile
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -q -B -DskipTests dependency:go-offline
COPY src src
```

Docker caches layers. Your dependencies usually change less often than your source code. If only `src` changes, Docker can reuse the dependency layer and rebuild faster.

The final image runs as user `spring`, not as root. That does not make the app magically secure, but it removes an unnecessary privilege. If an application bug allows file writes or process execution inside the container, root makes the blast radius worse.

## Docker Ignore

The `.dockerignore` file keeps build output and editor noise out of the Docker build context:

```text
{% include-markdown "../../code/45-docker/maven/.dockerignore" comments=false %}
```

The build context is everything Docker sends to the build engine. If you forget `.dockerignore`, you can accidentally send `target/`, logs, local IDE files, or repository metadata. That makes builds slower and can leak local-only files into image layers.

## Build And Run

From the chapter's Maven directory:

```bash
docker build -t sb4-docs/45-docker:dev .
docker run --rm -p 8080:8080 sb4-docs/45-docker:dev
```

In another terminal:

```bash
curl -s localhost:8080/api/build
curl -s localhost:8080/actuator/health/readiness
```

You can also override the application port:

```bash
docker run --rm -e PORT=9090 -p 9090:9090 sb4-docs/45-docker:dev
```

Inside the container, the app listens on 9090 because `PORT=9090`. Outside the container, `-p 9090:9090` maps host port 9090 to container port 9090.

Port mapping is two separate ideas: the Spring Boot server port inside the container, and the host port Docker exposes outside the container. They often match in examples, but they do not have to.

## Tests

The tests prove the two things the image will rely on: the API endpoint and the readiness probe.

```java
{% include-markdown "../../code/45-docker/maven/src/test/java/dev/springboot4docs/ch_45_docker/ApplicationTests.java" comments=false %}
```

`@SpringBootTest(webEnvironment = RANDOM_PORT)` starts the app on a real local port. `@AutoConfigureRestTestClient` gives the test an HTTP client bound to that port. This is stronger than a plain context-load test because it proves JSON serialization, routing, Actuator, and HTTP status behavior.

Run the chapter from the Maven directory:

=== "Maven"
    ```bash
    ./mvnw test
    ```

=== "Gradle"
    ```bash
    ./gradlew test
    ```

## Buildpacks Alternative

Spring Boot can also build an OCI image without a hand-written Dockerfile:

```bash
./mvnw spring-boot:build-image -Dspring-boot.build-image.imageName=sb4-docs/45-docker:buildpack
```

That command uses Cloud Native Buildpacks. Buildpacks inspect the application, choose a JVM runtime, lay out the image, and add metadata. The hand-written Dockerfile is useful because you can see every layer. Buildpacks are useful because they encode platform best practices and reduce custom Dockerfile maintenance.

Neither choice is universally better. Use a Dockerfile when you need explicit control. Use buildpacks when the standard Java image shape is enough and you want the platform to manage more of the details.

## Production Rules

Do not put secrets in the image. Images are copied, cached, pushed to registries, scanned, and retained. Secrets belong in runtime configuration: environment variables, mounted secret files, or a platform secret manager.

Do not rely on mutable image tags such as `latest` for production rollouts. Use a version, Git SHA, or build number. A deployment should say exactly which image it runs.

Keep the image small enough to move quickly, but do not optimize size before correctness. The first priorities are repeatable builds, passing tests, non-root runtime, health probes, and clear runtime configuration.

Chapter 46 moves this packaging work into GitHub Actions so CI can build and test the project automatically.
