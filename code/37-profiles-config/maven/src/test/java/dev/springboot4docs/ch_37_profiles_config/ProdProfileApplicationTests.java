package dev.springboot4docs.ch_37_profiles_config;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.MOCK,
		properties = "DB_URL=jdbc:postgresql://db.example.test/profiles")
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class ProdProfileApplicationTests {

	@Autowired
	private MockMvcTester mvc;

	@Test
	void prodProfileOverridesTheBaseGreeting() {
		this.mvc.get().uri("/greet")
				.assertThat()
				.hasStatus(200)
				.bodyText()
				.isEqualTo("Hello from production");
	}

	@Test
	void prodProfileReadsDatabaseUrlFromTheEnvironment() {
		this.mvc.get().uri("/info")
				.assertThat()
				.hasStatus(200)
				.bodyJson()
				.extractingPath("$.databaseUrl")
				.asString()
				.isEqualTo("jdbc:postgresql://db.example.test/profiles");
	}

}
