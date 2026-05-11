package dev.springboot4docs.ch_37_profiles_config;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TestProfileApplicationTests {

	@Autowired
	private MockMvcTester mvc;

	@Test
	void testProfileOverridesTheBaseGreeting() {
		this.mvc.get().uri("/greet")
				.assertThat()
				.hasStatus(200)
				.bodyText()
				.isEqualTo("Hello from tests");
	}

	@Test
	void testProfileCanOverrideSecretDefaults() {
		this.mvc.get().uri("/info")
				.assertThat()
				.hasStatus(200)
				.bodyJson()
				.extractingPath("$.secretApiKey")
				.asString()
				.isEqualTo("test-key");
	}

}
