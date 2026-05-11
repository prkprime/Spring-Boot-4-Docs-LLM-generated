package dev.springboot4docs.ch_34_oauth2_login;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;

@WebMvcTest(WebController.class)
@Import(SecurityConfig.class)
class OAuth2LoginWebMvcTest {

	@Autowired
	private MockMvcTester mvc;

	@Test
	void homeAllowsAnonymousRequestsAndShowsLoginLinks() {
		this.mvc.get().uri("/")
				.assertThat()
				.hasStatus(200)
				.bodyText()
				.contains("Sign in with GitHub", "/oauth2/authorization/github",
						"Sign in with Google", "/oauth2/authorization/google");
	}

	@Test
	void meRejectsAnonymousRequests() {
		this.mvc.get().uri("/me")
				.assertThat()
				.hasStatus(401);
	}

	@Test
	void meAcceptsOAuth2User() {
		OAuth2User user = new DefaultOAuth2User(
				List.of(new SimpleGrantedAuthority("ROLE_USER")),
				Map.of("id", "alice-123", "name", "Alice Github"),
				"name");

		this.mvc.get().uri("/me")
				.with(oauth2Login().oauth2User(user))
				.assertThat()
				.hasStatus(200)
				.bodyJson()
				.extractingPath("$.name")
				.asString()
				.isEqualTo("Alice Github");
	}

	@Test
	void meAcceptsOidcUserAndReturnsIdTokenClaims() {
		this.mvc.get().uri("/me")
				.with(oidcLogin().idToken((idToken) -> idToken
						.claim(IdTokenClaimNames.SUB, "google-subject-123")
						.claim("name", "Alice Google")
						.claim("email", "alice@example.com")
						.issuedAt(Instant.parse("2026-01-01T00:00:00Z"))
						.expiresAt(Instant.parse("2026-01-01T01:00:00Z"))))
				.assertThat()
				.hasStatus(200)
				.bodyJson()
				.extractingPath("$.idTokenClaims.email")
				.asString()
				.isEqualTo("alice@example.com");
	}

}
