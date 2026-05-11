# Appendix D — Migrating Spring Boot 3 To 4

Migrate one step at a time. First move to the latest Spring Boot 3.x patch release and remove deprecations. Then upgrade to Spring Boot 4 and fix compile errors before changing behavior.

Important migration areas:

- Java baseline: run on a supported Java version. This course recommends Java 25 for the samples.
- Jakarta EE 11: Servlet, JPA, Bean Validation, and related APIs move to their newer Jakarta levels.
- Modularized Spring Boot packages: several test and auto-configuration annotations moved to more specific packages.
- Jackson 3: application JSON uses the `tools.jackson` line. Libraries that still expose `com.fasterxml.jackson` APIs may need careful dependency boundaries.
- JUnit 6: test engines and annotations are updated with the Boot 4 test stack.
- Configuration properties: use the Spring Boot configuration properties migrator while upgrading existing applications, then remove it after the migration.

Examples of package changes used in this course:

```java
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
```

Do not use the migrator in new applications. It is a temporary compatibility tool for existing apps so you can see renamed or removed properties at runtime.

