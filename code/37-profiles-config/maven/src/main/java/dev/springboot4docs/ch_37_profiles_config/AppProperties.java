package dev.springboot4docs.ch_37_profiles_config;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app")
public record AppProperties(
		@NotBlank String name,
		@NotBlank String greeting,
		@NotBlank String version,
		@NotBlank String secretApiKey,
		@NotBlank String databaseUrl) {
}
