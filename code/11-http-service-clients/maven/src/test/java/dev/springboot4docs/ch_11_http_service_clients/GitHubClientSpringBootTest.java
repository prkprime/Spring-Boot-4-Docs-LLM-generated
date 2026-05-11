package dev.springboot4docs.ch_11_http_service_clients;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SpringBootTest(properties = "spring.http.client.service.dev.springboot4docs.ch_11_http_service_clients.GitHubClient.base-url=http://localhost")
class GitHubClientSpringBootTest {

	@Autowired
	private GitHubClient github;

	@Autowired
	private MockServerHolder mockServer;

	@Test
	void generatedClientCallsConfiguredBaseUrl() {
		this.mockServer.server.expect(requestTo("http://localhost/repos/spring-projects/spring-boot"))
				.andRespond(withSuccess("""
						{
						  "full_name": "spring-projects/spring-boot",
						  "description": "Stubbed repository",
						  "stargazers_count": 42
						}
						""", MediaType.APPLICATION_JSON));

		GitHubRepo repo = this.github.getRepo("spring-projects", "spring-boot");

		assertThat(repo.full_name()).isEqualTo("spring-projects/spring-boot");
		assertThat(repo.description()).isEqualTo("Stubbed repository");
		assertThat(repo.stargazers_count()).isEqualTo(42);
		this.mockServer.server.verify();
	}

	@TestConfiguration
	static class MockServerConfig {

		@Bean
		MockServerHolder mockServerHolder() {
			return new MockServerHolder();
		}

		@Bean
		RestClientHttpServiceGroupConfigurer mockRestServiceServerConfigurer(MockServerHolder holder) {
			return (groups) -> groups.forEachClient((group, builder) -> holder.server = MockRestServiceServer.bindTo(builder).build());
		}

	}

	static class MockServerHolder {

		private MockRestServiceServer server;

	}

}
