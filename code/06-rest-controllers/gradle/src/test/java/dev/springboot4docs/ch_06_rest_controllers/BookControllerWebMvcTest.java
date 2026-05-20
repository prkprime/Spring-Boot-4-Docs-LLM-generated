package dev.springboot4docs.ch_06_rest_controllers;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebMvcTest(BookController.class)
class BookControllerWebMvcTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private BookRepository bookRepository;

    @Test
    void listReturnsBooks() {
        when(this.bookRepository.findAll()).thenReturn(List.of(
                new Book(1L, "Dune", "Frank Herbert"),
                new Book(2L, "The Left Hand of Darkness", "Ursula K. Le Guin")));

        this.mvc.get().uri("/books")
                .assertThat()
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .extractingPath("$")
                .asArray()
                .hasSize(2);
    }

    @Test
    void getByIdReturnsBook() {
        when(this.bookRepository.findById(1L)).thenReturn(Optional.of(new Book(1L, "Dune", "Frank Herbert")));

        this.mvc.get().uri("/books/{id}", 1)
                .assertThat()
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .extractingPath("$.title")
                .isEqualTo("Dune");
    }

    @Test
    void getByIdReturnsNotFoundWhenMissing() {
        when(this.bookRepository.findById(99L)).thenReturn(Optional.empty());

        this.mvc.get().uri("/books/{id}", 99)
                .assertThat()
                .hasStatus(404);
    }

    @Test
    void postCreatesBook() {
        when(this.bookRepository.create(new Book(null, "Dune", "Frank Herbert")))
                .thenReturn(new Book(1L, "Dune", "Frank Herbert"));

        this.mvc.post().uri("/books")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"Dune","author":"Frank Herbert"}
                        """)
                .assertThat()
                .hasStatus(201)
                .hasHeader(HttpHeaders.LOCATION, "http://localhost/books/1")
                .bodyJson()
                .extractingPath("$.id")
                .isEqualTo(1);
    }

    @Test
    void putUpdatesBook() {
        when(this.bookRepository.update(1L, new Book(null, "Dune Messiah", "Frank Herbert")))
                .thenReturn(Optional.of(new Book(1L, "Dune Messiah", "Frank Herbert")));

        this.mvc.put().uri("/books/{id}", 1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"Dune Messiah","author":"Frank Herbert"}
                        """)
                .assertThat()
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.title")
                .isEqualTo("Dune Messiah");
    }

    @Test
    void deleteRemovesBook() {
        when(this.bookRepository.findById(1L)).thenReturn(Optional.empty());

        this.mvc.delete().uri("/books/{id}", 1)
                .assertThat()
                .hasStatus(204);

        verify(this.bookRepository).deleteById(1L);

        this.mvc.get().uri("/books/{id}", 1)
                .assertThat()
                .hasStatus(404);
    }

    @Test
    void searchFiltersByQueryParameters() {
        when(this.bookRepository.search(Optional.of("Dune"), Optional.of("Frank")))
                .thenReturn(List.of(new Book(1L, "Dune", "Frank Herbert")));

        this.mvc.get().uri("/books/search")
                .param("title", "Dune")
                .param("author", "Frank")
                .assertThat()
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .extractingPath("$[0].author")
                .isEqualTo("Frank Herbert");
    }

}
