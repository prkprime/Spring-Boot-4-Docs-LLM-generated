package dev.springboot4docs.ch_42_testing_slices;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@WebMvcTest(NoteController.class)
class NoteControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private NoteService notes;

    @Test
    void listsNotesAsJson() {
        given(this.notes.list()).willReturn(List.of(new Note("Controller slice", "No repository needed.")));

        this.mvc.get().uri("/notes")
            .assertThat()
            .hasStatusOk()
            .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            .bodyJson()
            .extractingPath("$[0].title")
            .isEqualTo("Controller slice");
    }

    @Test
    void createsANote() {
        Note saved = new Note("New note", "Created through MVC.");
        setId(saved, 42L);
        given(this.notes.create("New note", "Created through MVC.")).willReturn(saved);

        this.mvc.post().uri("/notes")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {"title":"New note","body":"Created through MVC."}
                    """)
            .assertThat()
            .hasStatus(201)
            .hasHeader("Location", "http://localhost/notes/42")
            .bodyJson()
            .extractingPath("$.id")
            .isEqualTo(42);
    }

    @Test
    void returnsNotFoundForMissingNotes() {
        given(this.notes.get(99L)).willThrow(new NoteNotFoundException());

        this.mvc.get().uri("/notes/{id}", 99)
            .assertThat()
            .hasStatus(404);
    }

    @Test
    void deletesANote() {
        this.mvc.delete().uri("/notes/{id}", 7)
            .assertThat()
            .hasStatus(204);

        verify(this.notes).delete(7L);
    }

    private static void setId(Note note, Long id) {
        try {
            var field = Note.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(note, id);
        }
        catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

}
