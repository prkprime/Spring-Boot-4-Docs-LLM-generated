package dev.springboot4docs.ch_31_csrf_headers;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

@WebMvcTest(WidgetController.class)
@Import(ApiOnlySecurityConfig.class)
@ActiveProfiles("api")
class ApiOnlySecurityWebMvcTest {

	@Autowired
	private MockMvcTester mvc;

	@Test
	void postWithoutCsrfTokenIsAccepted() {
		this.mvc.post().uri("/widgets")
				.with(httpBasic("alice", "secret"))
				.assertThat()
				.hasStatusOk();
	}

}
