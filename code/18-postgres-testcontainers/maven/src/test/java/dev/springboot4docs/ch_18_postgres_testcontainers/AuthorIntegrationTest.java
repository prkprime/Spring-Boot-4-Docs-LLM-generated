package dev.springboot4docs.ch_18_postgres_testcontainers;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class AuthorIntegrationTest {

	@Autowired
	private RestTestClient restTestClient;

	@Test
	void listSeededAuthors() {
		this.restTestClient.get().uri("/authors")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$[*].email").value(emails -> assertThat(emails).asList().contains("octavia@example.com"));
	}

	@Test
	void createReadAndDeleteAuthorOverHttp() {
		Author created = this.restTestClient.post().uri("/authors")
				.contentType(MediaType.APPLICATION_JSON)
				.body("""
						{"name":"Becky Chambers","email":"becky@example.com","bio":"Author of hopeful science fiction."}
						""")
				.exchange()
				.expectStatus().isCreated()
				.expectBody(Author.class)
				.returnResult()
				.getResponseBody();

		assertThat(created).isNotNull();
		assertThat(created.getId()).isNotNull();
		assertThat(created.getCreatedAt()).isNotNull();

		this.restTestClient.get().uri("/authors/{id}", created.getId())
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.email").isEqualTo("becky@example.com")
				.jsonPath("$.bio").isEqualTo("Author of hopeful science fiction.");

		this.restTestClient.delete().uri("/authors/{id}", created.getId())
				.exchange()
				.expectStatus().isNoContent();

		this.restTestClient.get().uri("/authors/{id}", created.getId())
				.exchange()
				.expectStatus().isNotFound();
	}

}
