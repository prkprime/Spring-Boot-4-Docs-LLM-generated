package dev.springboot4docs.ch_11_http_service_clients;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebMvcTest(RepoController.class)
class RepoControllerWebMvcTest {

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private GitHubClient github;

	@Test
	void returnsRepoFromHttpServiceClient() {
		when(this.github.getRepo("spring-projects", "spring-boot"))
				.thenReturn(new GitHubRepo("spring-projects/spring-boot", "Spring Boot", 76000));

		this.mvc.get().uri("/repos/{owner}/{repo}", "spring-projects", "spring-boot")
				.assertThat()
				.hasStatusOk()
				.hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.bodyJson()
				.extractingPath("$.full_name").asString().isEqualTo("spring-projects/spring-boot");

		verify(this.github).getRepo("spring-projects", "spring-boot");
	}

}
