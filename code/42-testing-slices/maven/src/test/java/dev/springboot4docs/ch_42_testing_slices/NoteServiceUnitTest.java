package dev.springboot4docs.ch_42_testing_slices;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NoteServiceUnitTest {

	@Mock
	private NoteRepository notes;

	@Test
	void createsANoteThroughTheRepository() {
		Note saved = new Note("Slice tests", "Keep Spring tests focused.");
		given(this.notes.save(any(Note.class))).willReturn(saved);

		NoteService service = new NoteService(this.notes);
		Note note = service.create("Slice tests", "Keep Spring tests focused.");

		assertThat(note.getTitle()).isEqualTo("Slice tests");
		verify(this.notes).save(any(Note.class));
	}

	@Test
	void listsExistingNotes() {
		given(this.notes.findAll()).willReturn(List.of(new Note("One", "Body")));

		NoteService service = new NoteService(this.notes);

		assertThat(service.list()).extracting(Note::getTitle).containsExactly("One");
	}

	@Test
	void throwsWhenANoteIsMissing() {
		given(this.notes.findById(99L)).willReturn(Optional.empty());

		NoteService service = new NoteService(this.notes);

		assertThatThrownBy(() -> service.get(99L)).isInstanceOf(NoteNotFoundException.class);
	}

}
