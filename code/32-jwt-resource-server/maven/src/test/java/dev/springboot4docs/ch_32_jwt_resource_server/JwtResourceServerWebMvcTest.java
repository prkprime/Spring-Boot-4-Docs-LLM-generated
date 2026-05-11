package dev.springboot4docs.ch_32_jwt_resource_server;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@WebMvcTest(ApiController.class)
@Import(SecurityConfig.class)
class JwtResourceServerWebMvcTest {

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JwtAuthenticationConverter jwtAuthenticationConverter;

	@Test
	void publicEndpointAllowsAnonymousRequests() {
		this.mvc.get().uri("/public/info")
				.assertThat()
				.hasStatus(200)
				.bodyText()
				.isEqualTo("public");
	}

	@Test
	void apiEndpointRejectsAnonymousRequests() {
		this.mvc.get().uri("/api/me")
				.assertThat()
				.hasStatus(401);
	}

	@Test
	void apiEndpointAcceptsJwt() {
		this.mvc.get().uri("/api/me")
				.with(jwt().jwt((token) -> token
						.subject("alice")
						.claim("scope", "read")))
				.assertThat()
				.hasStatus(200)
				.bodyJson()
				.extractingPath("$.subject")
				.asString()
				.isEqualTo("alice");
	}

	@Test
	void adminEndpointAcceptsAdminScope() {
		this.mvc.get().uri("/api/admin/secrets")
				.with(jwt().jwt((token) -> token.claim("scope", "admin")))
				.assertThat()
				.hasStatus(200)
				.bodyText()
				.isEqualTo("admin secrets");
	}

	@Test
	void adminEndpointRejectsReadScope() {
		this.mvc.get().uri("/api/admin/secrets")
				.with(jwt().jwt((token) -> token.claim("scope", "read")))
				.assertThat()
				.hasStatus(403);
	}

	@Test
	void rolesClaimCanBeMappedToRoleAuthorities() {
		Jwt jwt = Jwt.withTokenValue("token")
				.header("alg", "none")
				.subject("alice")
				.claim("roles", List.of("ADMIN"))
				.build();

		assertThat(this.jwtAuthenticationConverter.convert(jwt).getAuthorities())
				.contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
	}

	@TestConfiguration
	static class TestJwtDecoderConfig {

		@Bean
		JwtDecoder jwtDecoder() {
			return (token) -> {
				throw new AssertionError("Use jwt() in MVC tests instead of decoding real bearer tokens");
			};
		}

	}

}
