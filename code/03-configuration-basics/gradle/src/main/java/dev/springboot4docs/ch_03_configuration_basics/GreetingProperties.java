package dev.springboot4docs.ch_03_configuration_basics;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("greeting")
public record GreetingProperties(String prefix, String audience, String punctuation) {
}
