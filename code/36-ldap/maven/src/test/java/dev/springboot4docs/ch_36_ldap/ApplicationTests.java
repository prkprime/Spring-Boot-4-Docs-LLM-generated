package dev.springboot4docs.ch_36_ldap;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ApplicationTests {

	@Autowired
	private MockMvcTester mvc;

	@Test
	void apiMeRequiresAuthentication() {
		this.mvc.get().uri("/api/me")
				.assertThat()
				.hasStatus(401);
	}

	@Test
	void aliceCanAuthenticateWithHttpBasic() {
		this.mvc.get().uri("/api/me")
				.with(httpBasic("alice", "secret"))
				.assertThat()
				.hasStatusOk()
				.bodyJson()
				.extractingPath("$.username")
				.asString()
				.isEqualTo("alice");
	}

	@Test
	void aliceCanUseAdminEndpointThroughLdapGroupMembership() {
		this.mvc.get().uri("/api/admin/secrets")
				.with(httpBasic("alice", "secret"))
				.assertThat()
				.hasStatusOk()
				.bodyText()
				.isEqualTo("admin secrets");
	}

	@Test
	void bobCannotUseAdminEndpointWithoutGroupMembership() {
		this.mvc.get().uri("/api/admin/secrets")
				.with(httpBasic("bob", "secret"))
				.assertThat()
				.hasStatus(403);
	}

}
