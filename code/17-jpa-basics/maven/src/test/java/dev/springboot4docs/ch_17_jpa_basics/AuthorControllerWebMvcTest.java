package dev.springboot4docs.ch_17_jpa_basics;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebMvcTest(AuthorController.class)
class AuthorControllerWebMvcTest {

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private AuthorService authorService;

	@Test
	void listReturnsAuthors() {
		when(this.authorService.findAll()).thenReturn(List.of(
				author(1L, "Octavia Butler", "octavia@example.com"),
				author(2L, "Ursula K. Le Guin", "ursula@example.com")));

		this.mvc.get().uri("/authors")
				.assertThat()
				.hasStatusOk()
				.hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.bodyJson()
				.extractingPath("$")
				.asArray()
				.hasSize(2);
	}

	@Test
	void getByIdReturnsAuthor() {
		when(this.authorService.findById(1L))
				.thenReturn(Optional.of(author(1L, "Octavia Butler", "octavia@example.com")));

		this.mvc.get().uri("/authors/{id}", 1)
				.assertThat()
				.hasStatusOk()
				.hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.bodyJson()
				.extractingPath("$.email")
				.isEqualTo("octavia@example.com");
	}

	@Test
	void getByIdReturnsNotFoundWhenMissing() {
		when(this.authorService.findById(99L)).thenReturn(Optional.empty());

		this.mvc.get().uri("/authors/{id}", 99)
				.assertThat()
				.hasStatus(404);
	}

	@Test
	void postCreatesAuthor() {
		when(this.authorService.create("Octavia Butler", "octavia@example.com"))
				.thenReturn(author(1L, "Octavia Butler", "octavia@example.com"));

		this.mvc.post().uri("/authors")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Octavia Butler","email":"octavia@example.com"}
						""")
				.assertThat()
				.hasStatus(201)
				.hasHeader(HttpHeaders.LOCATION, "http://localhost/authors/1")
				.bodyJson()
				.extractingPath("$.id")
				.isEqualTo(1);
	}

	@Test
	void deleteReturnsNoContent() {
		this.mvc.delete().uri("/authors/{id}", 1)
				.assertThat()
				.hasStatus(204);

		verify(this.authorService).deleteById(1L);
	}

	private static Author author(Long id, String name, String email) {
		Author author = new Author(name, email);
		author.setId(id);
		return author;
	}

}
