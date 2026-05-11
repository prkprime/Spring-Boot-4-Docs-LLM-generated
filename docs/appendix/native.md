# Appendix B — GraalVM Native Image

Spring Boot can build GraalVM native images directly or through Cloud Native Buildpacks. Native images trade build time and some dynamic runtime flexibility for fast startup and lower memory use.

The common buildpack path is:

```bash
./mvnw -Pnative spring-boot:build-image
```

Native images work best when the framework can see reflection, resource, proxy, and serialization needs ahead of time. Spring Boot and Spring Framework contribute many hints automatically. Your own dynamic code may need explicit runtime hints.

A typical hint class registers reflective access for a type that a library creates dynamically:

```java
class MyRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.reflection().registerType(MyDto.class, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
    }
}
```

Native image is not a default requirement for every service. It is a deployment choice. It is attractive for serverless functions, command-line tools, scale-to-zero platforms, and memory-constrained environments. For long-running high-throughput services, a regular JVM image can still be the simpler and faster steady-state option.

