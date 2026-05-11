package dev.springboot4docs.ch_31_csrf_headers;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

@WebMvcTest(WidgetController.class)
@Import(SpaSecurityConfig.class)
@ActiveProfiles("spa")
class SpaSecurityWebMvcTest {

	@Autowired
	private MockMvcTester mvc;

	@Test
	void csrfEndpointReturnsATokenAndPostAcceptsItInTheXsrfHeader() {
		MvcTestResult csrfResult = this.mvc.get().uri("/csrf")
				.with(httpBasic("alice", "secret"))
				.exchange();

		csrfResult.assertThat().hasStatusOk();

		String token = csrfResult.assertThat()
				.bodyJson()
				.extractingPath("$.token")
				.asString()
				.actual();
		Cookie xsrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");

		assertThat(token).isNotBlank();
		assertThat(xsrfCookie).isNotNull();

		this.mvc.post().uri("/widgets")
				.with(httpBasic("alice", "secret"))
				.cookie(xsrfCookie)
				.header("X-XSRF-TOKEN", token)
				.assertThat()
				.hasStatusOk();
	}

	@Test
	void postWithoutCsrfTokenIsRejected() {
		this.mvc.post().uri("/widgets")
				.with(httpBasic("alice", "secret"))
				.assertThat()
				.hasStatus(403);
	}

}
