package dev.springboot4docs.ch_42_testing_slices;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class NoteRepositoryTest {

    @Autowired
    private NoteRepository notes;

    @Test
    void savesAndFindsANote() {
        Note saved = this.notes.save(new Note("Repository slice", "JPA is real here."));

        assertThat(this.notes.findById(saved.getId()))
            .hasValueSatisfying((note) -> assertThat(note.getBody()).isEqualTo("JPA is real here."));
    }

    @Test
    void searchesByTitleIgnoringCase() {
        this.notes.save(new Note("Testing slices", "Use narrow contexts."));
        this.notes.save(new Note("Production logging", "Different chapter."));

        assertThat(this.notes.findByTitleContainingIgnoreCase("SLICE"))
            .extracting(Note::getTitle)
            .containsExactly("Testing slices");
    }

    @Test
    void rollsBackBetweenTests() {
        assertThat(this.notes.count()).isZero();
    }

}
