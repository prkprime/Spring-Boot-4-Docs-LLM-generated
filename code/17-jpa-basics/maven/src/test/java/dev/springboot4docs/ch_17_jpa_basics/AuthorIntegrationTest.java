package dev.springboot4docs.ch_17_jpa_basics;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class AuthorIntegrationTest {

	@Autowired
	private RestTestClient restTestClient;

	@Test
	void createReadAndDeleteAuthor() {
		this.restTestClient.post().uri("/authors")
				.contentType(MediaType.APPLICATION_JSON)
				.body("""
						{"name":"Octavia Butler","email":"octavia@example.com"}
						""")
				.exchange()
				.expectStatus().isCreated()
				.expectHeader().valueMatches("Location", ".*/authors/1$")
				.expectBody()
				.jsonPath("$.id").isEqualTo(1)
				.jsonPath("$.createdAt").exists();

		this.restTestClient.get().uri("/authors/{id}", 1)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.email").isEqualTo("octavia@example.com");

		this.restTestClient.delete().uri("/authors/{id}", 1)
				.exchange()
				.expectStatus().isNoContent();

		this.restTestClient.get().uri("/authors/{id}", 1)
				.exchange()
				.expectStatus().isNotFound();
	}

}
