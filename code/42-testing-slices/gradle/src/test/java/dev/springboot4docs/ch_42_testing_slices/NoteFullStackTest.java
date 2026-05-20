package dev.springboot4docs.ch_42_testing_slices;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest
@AutoConfigureRestTestClient
class NoteFullStackTest {

    @Autowired
    private RestTestClient rest;

    @Autowired
    private NoteRepository notes;

    @BeforeEach
    void deleteExistingNotes() {
        this.notes.deleteAll();
    }

    @Test
    void createsAndReadsANoteThroughHttp() {
        this.rest.post().uri("/notes")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""
                    {"title":"Full stack","body":"HTTP, MVC, service, JPA, and JSON."}
                    """)
            .exchange()
            .expectStatus().isCreated()
            .expectBody()
            .jsonPath("$.id").isNumber()
            .jsonPath("$.title").isEqualTo("Full stack");

        this.rest.get().uri("/notes")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$[0].body").isEqualTo("HTTP, MVC, service, JPA, and JSON.");
    }

    @Test
    void updatesAndDeletesANoteThroughHttp() {
        Note saved = this.notes.save(new Note("Draft", "Before"));

        this.rest.put().uri("/notes/{id}", saved.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .body("""
                    {"title":"Published","body":"After"}
                    """)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.title").isEqualTo("Published");

        this.rest.delete().uri("/notes/{id}", saved.getId())
            .exchange()
            .expectStatus().isNoContent()
            .expectBody().isEmpty();

        this.rest.get().uri("/notes/{id}", saved.getId())
            .exchange()
            .expectStatus().isNotFound();
    }

}
