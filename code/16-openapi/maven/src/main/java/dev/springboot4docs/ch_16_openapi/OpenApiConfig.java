package dev.springboot4docs.ch_16_openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {

	@Bean
	OpenAPI customOpenAPI() {
		return new OpenAPI()
				.info(new Info()
						.title("Widget API")
						.version("1.0.0")
						.contact(new Contact()
								.name("Spring Boot 4 Docs")
								.email("docs@example.com")
								.url("https://example.com/support")));
	}

}
