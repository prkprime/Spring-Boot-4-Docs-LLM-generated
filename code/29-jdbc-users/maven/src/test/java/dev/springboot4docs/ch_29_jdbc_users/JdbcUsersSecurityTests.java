package dev.springboot4docs.ch_29_jdbc_users;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JdbcUsersSecurityTests {

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private AppUserRepository users;

	@BeforeEach
	void clearUsers() {
		this.users.deleteAll();
	}

	@Test
	void apiMeRequiresAuthentication() {
		this.mvc.get().uri("/api/me")
				.assertThat()
				.hasStatus(401);
	}

	@Test
	void signupCreatesAUserWhoCanAuthenticateWithHttpBasic() {
		this.mvc.post().uri("/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "username": "ada",
						  "password": "secret"
						}
						""")
				.assertThat()
				.hasStatus(201)
				.bodyJson()
				.extractingPath("$.username")
				.asString()
				.isEqualTo("ada");

		AppUser saved = this.users.findByUsername("ada").orElseThrow();
		assertThat(saved.getPasswordHash()).startsWith("{bcrypt}");

		this.mvc.get().uri("/api/me")
				.with(httpBasic("ada", "secret"))
				.assertThat()
				.hasStatusOk()
				.bodyText()
				.isEqualTo("ada");
	}

	@Test
	void successfulLoginUpgradesLegacyPasswordHash() {
		this.users.save(new AppUser("legacy", "{noop}plain", "USER"));

		this.mvc.get().uri("/api/me")
				.with(httpBasic("legacy", "plain"))
				.assertThat()
				.hasStatusOk()
				.bodyText()
				.isEqualTo("legacy");

		AppUser upgraded = this.users.findByUsername("legacy").orElseThrow();
		assertThat(upgraded.getPasswordHash()).startsWith("{bcrypt}");
	}

}
