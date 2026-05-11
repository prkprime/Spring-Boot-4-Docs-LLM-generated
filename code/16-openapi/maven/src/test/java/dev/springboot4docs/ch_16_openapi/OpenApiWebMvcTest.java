package dev.springboot4docs.ch_16_openapi;

import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.configuration.SpringDocSpecPropertiesConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(WidgetController.class)
@Import(OpenApiConfig.class)
@ImportAutoConfiguration({ SpringDocConfiguration.class, SpringDocConfigProperties.class,
		SpringDocSpecPropertiesConfiguration.class, SpringDocWebMvcConfiguration.class })
class OpenApiWebMvcTest {

	@Autowired
	private MockMvcTester mvc;

	@Test
	void apiDocsExposeWidgetPaths() {
		this.mvc.get().uri("/v3/api-docs")
				.assertThat()
				.hasStatusOk()
				.hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.bodyJson()
				.extractingPath("$.paths")
				.asMap()
				.containsKeys("/widgets", "/widgets/{id}");
	}

}
