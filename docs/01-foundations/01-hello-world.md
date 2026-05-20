# Hello World

## What we'll build

We'll build a one-endpoint Spring Boot 4 application and exercise that endpoint with a focused web test.

## Generate the project (Spring Initializr)

[Spring Initializr](https://start.spring.io/) is the quickest way to create a Spring Boot project that already has the right build file, wrapper scripts, source folders, and starter dependencies.

You choose the project shape in the browser, click **Generate**, unzip the result, and start coding. Initializr can also be invoked from the command line with `spring init`, but the URL is the path of least resistance for this first pass.

For this chapter, the project was generated with these choices:

- **Project:** Maven or Gradle - Kotlin
- **Language:** Java
- **Spring Boot:** 4.0.6
- **Java:** 25
- **Dependencies:** Web (represented by `spring-boot-starter-webmvc` in Maven or Gradle)

!!! note "Dual-Build Support"
    This guide provides full, first-class support for Maven and Gradle. Each chapter contains fully configured projects for both build systems.

The generated projects for this chapter live here:

=== "Maven"
    ```text
    code/01-hello-world/maven/
    ```

=== "Gradle"
    ```text
    code/01-hello-world/gradle/
    ```

## What's in the box

Initializr gives you a small but complete application. The directory structures look like this:

=== "Maven Structure"
    ```text
    code/01-hello-world/maven/
    ├── pom.xml
    ├── mvnw
    ├── src/main/java
    ├── src/main/resources/application.properties
    └── src/test/java
    ```

=== "Gradle Structure"
    ```text
    code/01-hello-world/gradle/
    ├── build.gradle.kts
    ├── settings.gradle.kts
    ├── gradlew
    ├── src/main/java
    ├── src/main/resources/application.properties
    └── src/test/java
    ```

The important files are:

`pom.xml` / `build.gradle.kts`

The build files. They declare the Spring Boot version, Java version, dependencies (like `spring-boot-starter-webmvc` and test support), and build plugins. The build tool reads these to know what to download and how to build the app.

`mvnw` / `gradlew`

The wrapper scripts for macOS and Linux. They let you run this project with `./mvnw` or `./gradlew` even if you do not have Maven or Gradle installed globally. There are also `.cmd` wrappers for Windows.

`src/main/java`

The production Java source tree. This is where the application classes and controllers live.

`src/main/resources/application.properties`

The configuration file. It is empty in this chapter because we are using Spring Boot's defaults. Later chapters will configure server, logging, and application settings here.

`src/test/java`

The test Java source tree. This is where the web-layer test lives. The build tool compiles and runs these tests during the test phase.

## The Build Configuration, line by line

The build configuration starts by defining the build engine and its parent defaults:

=== "Maven (pom.xml)"
    ```xml
    {% include-markdown "../../code/01-hello-world/maven/pom.xml" start="<parent>" end="</parent>" comments=false %}
    ```

=== "Gradle (build.gradle.kts)"
    ```kotlin
    {% include-markdown "../../code/01-hello-world/gradle/build.gradle.kts" start="plugins {" end="}" comments=false %}
    ```

In Maven, `spring-boot-starter-parent` gives the project managed dependency versions and plugin defaults. In Gradle, the `plugins` block imports the Spring Boot and Dependency Management plugins to do the same.

!!! note "Version string"
    The parent version in the finished project is `4.0.6`. Maven and Gradle resolve that exact version from the repository.

The project metadata comes next:

=== "Maven (pom.xml)"
    ```xml
    {% include-markdown "../../code/01-hello-world/maven/pom.xml" start="<groupId>dev.springboot4docs</groupId>" end="<licenses>" comments=false %}
    ```

=== "Gradle (build.gradle.kts)"
    ```kotlin
    {% include-markdown "../../code/01-hello-world/gradle/build.gradle.kts" start="group =" end="version =" comments=false %}
    ```

These values identify the application. They do not change the runtime behavior in this chapter, but identify the packages.

The Java version is declared next:

=== "Maven (pom.xml)"
    ```xml
    {% include-markdown "../../code/01-hello-world/maven/pom.xml" start="<properties>" end="</properties>" comments=false %}
    ```

=== "Gradle (build.gradle.kts)"
    ```kotlin
    {% include-markdown "../../code/01-hello-world/gradle/build.gradle.kts" start="java {" end="}" comments=false %}
    ```

This configures the build setup to compile the application for Java 25.

The web dependency is the first starter:

=== "Maven (pom.xml)"
    ```xml
    {% include-markdown "../../code/01-hello-world/maven/pom.xml" start="<artifactId>spring-boot-starter-webmvc</artifactId>" end="</dependency>" comments=false %}
    ```

=== "Gradle (build.gradle.kts)"
    ```kotlin
    {% include-markdown "../../code/01-hello-world/gradle/build.gradle.kts" start="    implementation(\"org.springframework.boot:spring-boot-starter-webmvc\")" end="    implementation(\"org.springframework.boot:spring-boot-starter-webmvc\")" comments=false %}
    ```

`spring-boot-starter-webmvc` brings in the servlet-based Spring MVC stack. It includes the pieces needed to receive HTTP requests, route them to controller methods, serialize responses, and run on an embedded servlet server (Tomcat).

!!! note "Spring Boot 4 only"
    Spring Boot 4 split the old `spring-boot-starter-web` starter. Use `spring-boot-starter-webmvc` for servlet applications and `spring-boot-starter-webflux` for reactive applications.

The test dependency is also a starter:

=== "Maven (pom.xml)"
    ```xml
    {% include-markdown "../../code/01-hello-world/maven/pom.xml" start="<artifactId>spring-boot-starter-webmvc-test</artifactId>" end="</dependency>" comments=false %}
    ```

=== "Gradle (build.gradle.kts)"
    ```kotlin
    {% include-markdown "../../code/01-hello-world/gradle/build.gradle.kts" start="    testImplementation(\"org.springframework.boot:spring-boot-starter-webmvc-test\")" end="    testImplementation(\"org.springframework.boot:spring-boot-starter-webmvc-test\")" comments=false %}
    ```

`spring-boot-starter-webmvc-test` brings in the Spring MVC testing support used by this chapter. It is narrower than the old all-in-one test setup because Spring Boot 4 modularizes the test starters.

At the bottom of the build configuration, we have the build tools and formatting configurations:

=== "Maven (pom.xml)"
    ```xml
    {% include-markdown "../../code/01-hello-world/maven/pom.xml" start="<build>" end="</build>" comments=false %}
    ```

=== "Gradle (build.gradle.kts)"
    ```kotlin
    {% include-markdown "../../code/01-hello-world/gradle/build.gradle.kts" start="spotless {" end="}" comments=false %}
    ```

The `spring-boot-maven-plugin` and standard Gradle tasks compile and run the application. We've also included the Spotless formatter plugin to automatically enforce style conventions.

## Application.java, line by line

Here is the application entry point:

```java
{% include-markdown "../../code/01-hello-world/maven/src/main/java/dev/springboot4docs/ch_01_hello_world/Application.java" comments=false %}
```

The `package` line puts the class in a Java package. Spring also uses this package as the starting point for component scanning, because the application class sits at the top of the app's source tree.

The import for `SpringApplication` brings in the class that can bootstrap a Spring application from a `main` method.

The import for `SpringBootApplication` brings in the main Spring Boot annotation:

```java
@SpringBootApplication
```

`@SpringBootApplication` is a composite annotation. It includes:

- `@SpringBootConfiguration`, which marks this class as a source of application configuration.
- `@EnableAutoConfiguration`, which lets Spring Boot configure common infrastructure based on the classpath.
- `@ComponentScan`, which tells Spring to find components in this package and below.

Do not worry about auto-configuration details yet. For now, read it as: "start a Spring Boot app from here and find the application classes nearby."

The class declaration is normal Java:

```java
public class Application {
```

The `main` method is the JVM entry point:

```java
public static void main(String[] args) {
```

Inside it, this line starts Spring Boot:

```java
SpringApplication.run(Application.class, args);
```

`Application.class` tells Spring which class is the starting configuration source. `args` passes through any command-line arguments.

When the app starts, Spring Boot creates an application context, finds the controller, configures Spring MVC, and starts an embedded web server. There is no separate Tomcat install in this project. The server is part of the application process.

## HelloController.java, line by line

The controller is the only application behavior in this chapter:

```java
{% include-markdown "../../code/01-hello-world/maven/src/main/java/dev/springboot4docs/ch_01_hello_world/HelloController.java" comments=false %}
```

The `package` line places the controller in the same package tree as `Application`. That matters because `@SpringBootApplication` scans this package and its children.

`@RestController` marks the class as a web controller whose return values should be written directly to the HTTP response body.

It is shorthand for two Spring MVC ideas:

- `@Controller`, which makes the class a web component.
- `@ResponseBody`, which says method return values are response bodies, not view names.

The class itself is plain Java:

```java
public class HelloController {
```

The method mapping is the route:

```java
@GetMapping("/hello")
```

`@GetMapping("/hello")` tells Spring MVC to call the next method when an HTTP `GET` request arrives for `/hello`.

The method returns a `String`:

```java
return "Hello, Spring Boot 4!";
```

Because the class is a `@RestController`, that string becomes the HTTP response body. The browser or `curl` receives the text directly.

## Run it

Start the app from the project directory:

=== "Maven"
    === "Maven"
    ```bash
    cd code/01-hello-world/maven
    ./mvnw spring-boot:run
    ```

=== "Gradle"
    ```bash
    cd code/01-hello-world/gradle
    ./gradlew bootRun
    ```

=== "Gradle"
    ```bash
    cd code/01-hello-world/gradle
    ./gradlew bootRun
    ```

Leave that terminal running. Spring Boot starts the embedded server on port `8080`.

In another terminal, call the endpoint:

```bash
curl -s localhost:8080/hello
```

Expected output:

```text
Hello, Spring Boot 4!
```

You just sent a real HTTP request to the app. Spring MVC matched `GET /hello`, called `HelloController`, and wrote the returned string to the response.

## The test

The test checks the controller without starting the whole application:

```java
{% include-markdown "../../code/01-hello-world/maven/src/test/java/dev/springboot4docs/ch_01_hello_world/HelloControllerTest.java" comments=false %}
```

`@WebMvcTest(HelloController.class)` creates a web MVC slice test.

A slice test loads only the part of Spring needed for the thing being tested. Here, that means the web layer around `HelloController`. It does not load the full application context, database connections, messaging clients, scheduled jobs, or other unrelated infrastructure.

The test uses `MockMvcTester`:

```java
private final MockMvcTester mvc;
```

!!! note "Spring Framework 7"
    `MockMvcTester` is new in Spring Framework 7. It provides a fluent AssertJ-style API for testing Spring MVC endpoints. The older `MockMvc.perform(...).andExpect(...)` chain is the legacy style; this guide uses `MockMvcTester`.

The test class receives the tester through constructor injection:

```java
HelloControllerTest(MockMvcTester mvc) {
    this.mvc = mvc;
}
```

Spring creates the `MockMvcTester` for the web slice, then passes it into the test class constructor. The test stores it in a field so test methods can use it.

The assertion is the whole request and response check:

```java
{% include-markdown "../../code/01-hello-world/maven/src/test/java/dev/springboot4docs/ch_01_hello_world/HelloControllerTest.java" start="mvc.get()" end=";" comments=false %}
```

Read the chain left to right.

`mvc.get()` starts a mock HTTP `GET` request.

`uri("/hello")` targets the same path you called with `curl`.

`assertThat()` switches into assertions for the result.

`hasStatusOk()` verifies HTTP status `200 OK`.

`bodyText().contains(...)` verifies that the response body contains the greeting.

This is not a browser test and it does not bind to port `8080`. It runs the controller through Spring MVC's test machinery inside the test process.

## Run the test

From the project directory, run:

=== "Maven"
    === "Maven"
    ```bash
    ./mvnw test
    ```

=== "Gradle"
    ```bash
    ./gradlew test
    ```

=== "Gradle"
    ```bash
    ./gradlew test
    ```

The end of the output should be green. For Maven, the build finishes with `BUILD SUCCESS`. For Gradle, it prints `BUILD SUCCESSFUL`.

If the test passes, the controller contract is locked in: `GET /hello` returns `200 OK` and includes `Hello, Spring Boot 4!` in the response body.

## What's next

Chapter 2 tightens the development loop so you can edit, run, and retest faster.
