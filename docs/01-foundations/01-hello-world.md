# Hello World

## What we'll build

We'll build a one-endpoint Spring Boot 4 application and exercise that endpoint with a focused web test.

## Generate the project (Spring Initializr)

[Spring Initializr](https://start.spring.io/) is the quickest way to create a Spring Boot project that already has the right build file, wrapper scripts, source folders, and starter dependencies.

You choose the project shape in the browser, click **Generate**, unzip the result, and start coding. Initializr can also be invoked from the command line with `spring init`, but the URL is the path of least resistance for this first pass.

For this chapter, the project was generated with these choices:

- **Project:** Maven
- **Language:** Java
- **Spring Boot:** 4.0.6
- **Java:** 25
- **Dependencies:** Web

!!! note "Maven first"
    This documentation currently scaffolds the Maven version only because the Gradle generator at `start.spring.io` is in an outage state. The Gradle version will be backfilled when the generator is usable again.

The generated project for this chapter lives here:

```text
code/01-hello-world/maven/
```

## What's in the box

Initializr gives you a small but complete application. The important pieces are:

```text
code/01-hello-world/maven/
├── pom.xml
├── mvnw
├── src/main/java
├── src/main/resources/application.properties
└── src/test/java
```

`pom.xml`

The Maven build file. It declares the Spring Boot version, the Java version, the web dependency, the test dependency, and the Spring Boot Maven plugin. Maven reads this file to know what to download and how to build the app.

`mvnw`

The Maven Wrapper for macOS and Linux. It lets you run this project with `./mvnw` even if Maven is not installed globally. There is also an `mvnw.cmd` wrapper for Windows.

`src/main/java`

The production Java source tree. This is where the application class and controller live. Maven compiles these files into the application you run.

`src/main/resources/application.properties`

The default configuration file. It is empty in this chapter because we are using Spring Boot's defaults. Later chapters will put server, logging, and application settings here.

`src/test/java`

The test Java source tree. This is where the web-layer test lives. Maven compiles and runs these tests when you execute `./mvnw test`.

## The pom.xml, line by line

The `pom.xml` starts by saying this project inherits from Spring Boot's Maven parent:

```xml
{% include-markdown "../../code/01-hello-world/maven/pom.xml" start="<parent>" end="</parent>" comments=false %}
```

`spring-boot-starter-parent` gives the project two big things.

First, it imports Spring Boot's managed dependency versions. That means you usually do not write versions for Spring libraries yourself. Boot has already tested a compatible set.

Second, it sets useful Maven plugin defaults. You can still override them, but the common Spring Boot build path works without much XML.

!!! note "Version string"
    The parent version in the finished project is `4.0.6`. Maven resolves that exact version from the repository. If a generated sample ever contains a suffix such as `.RELEASE`, replace it with the exact version used by the rest of this guide.

The project metadata comes next:

```xml
{% include-markdown "../../code/01-hello-world/maven/pom.xml" start="<groupId>dev.springboot4docs</groupId>" end="<licenses>" comments=false %}
```

These values identify the application. They do not change the runtime behavior in this chapter. They matter more when you publish artifacts or organize several projects.

The Java version is declared as a Maven property:

```xml
{% include-markdown "../../code/01-hello-world/maven/pom.xml" start="<properties>" end="</properties>" comments=false %}
```

`<java.version>25</java.version>` tells Spring Boot's build setup to compile the application for Java 25. Use the same Java version in your terminal when you run the wrapper commands.

The web dependency is the first starter:

```xml
{% include-markdown "../../code/01-hello-world/maven/pom.xml" start="<artifactId>spring-boot-starter-webmvc</artifactId>" end="</dependency>" comments=false %}
```

`spring-boot-starter-webmvc` brings in the servlet-based Spring MVC stack. It includes the pieces needed to receive HTTP requests, route them to controller methods, serialize responses, and run on an embedded servlet server.

!!! note "Spring Boot 4 only"
    Spring Boot 4 split the old `spring-boot-starter-web` starter. Use `spring-boot-starter-webmvc` for servlet applications and `spring-boot-starter-webflux` for reactive applications. If you are porting from Spring Boot 3, this dependency name is one of the first changes you will notice.

The test dependency is also a starter:

```xml
{% include-markdown "../../code/01-hello-world/maven/pom.xml" start="<artifactId>spring-boot-starter-webmvc-test</artifactId>" end="</dependency>" comments=false %}
```

`spring-boot-starter-webmvc-test` brings in the Spring MVC testing support used by this chapter. It is narrower than the old all-in-one test setup because Spring Boot 4 modularizes the test starters too.

!!! note "Spring Boot 4 only"
    The web MVC test starter is new in the Spring Boot 4 line. For this guide, we use the MVC-specific test starter because our application is MVC-specific.

At the bottom, the build uses the Spring Boot Maven plugin:

```xml
{% include-markdown "../../code/01-hello-world/maven/pom.xml" start="<build>" end="</build>" comments=false %}
```

The `spring-boot-maven-plugin` adds Spring Boot goals to Maven. In this chapter we use `spring-boot:run`, which compiles the code and starts the application from the Maven project.

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

```bash
cd code/01-hello-world/maven
./mvnw spring-boot:run
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

From the Maven project directory, run:

```bash
./mvnw test
```

The end of the output should be green. The exact timing can differ, but the final lines should look like this:

```text
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

If the test passes, the controller contract is locked in: `GET /hello` returns `200 OK` and includes `Hello, Spring Boot 4!` in the response body.

## What's next

Chapter 2 tightens the development loop so you can edit, run, and retest faster.
