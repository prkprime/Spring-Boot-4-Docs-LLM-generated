# Appendix E — Programmatic Bean Registration

Most Spring applications define beans with annotations or `@Bean` methods. Spring Framework also supports programmatic bean registration for cases where beans are discovered or assembled dynamically.

Programmatic registration is useful for framework code, plugin systems, generated clients, and infrastructure that needs to register many similar beans from metadata. It is usually unnecessary for normal application services.

The shape is:

```java
class ClientRegistrar implements BeanDefinitionRegistryPostProcessor {

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        RootBeanDefinition definition = new RootBeanDefinition(ApiClient.class);
        definition.getConstructorArgumentValues().addIndexedArgumentValue(0, "https://api.example.com");
        registry.registerBeanDefinition("apiClient", definition);
    }
}
```

The tradeoff is readability. Annotation configuration is easy to follow in an application codebase. Programmatic registration is more powerful, but the relationship between source code and runtime beans is less obvious. Use it when you are building infrastructure, not because it looks clever.

