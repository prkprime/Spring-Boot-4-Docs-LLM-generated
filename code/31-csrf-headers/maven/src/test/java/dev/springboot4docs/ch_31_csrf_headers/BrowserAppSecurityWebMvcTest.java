package dev.springboot4docs.ch_31_csrf_headers;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(WidgetController.class)
@Import(BrowserAppSecurityConfig.class)
@ActiveProfiles("default")
class BrowserAppSecurityWebMvcTest {

	@Autowired
	private MockMvcTester mvc;

	@Test
	@WithMockUser("alice")
	void postWithoutCsrfTokenIsRejected() {
		this.mvc.post().uri("/widgets")
				.assertThat()
				.hasStatus(403);
	}

	@Test
	@WithMockUser("alice")
	void postWithCsrfTokenIsAccepted() {
		this.mvc.post().uri("/widgets")
				.with(csrf())
				.assertThat()
				.hasStatusOk();
	}

}
