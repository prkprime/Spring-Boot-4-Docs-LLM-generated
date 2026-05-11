package dev.springboot4docs.ch_23_auditing;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notes")
class NoteController {

	private final NoteRepository notes;

	NoteController(NoteRepository notes) {
		this.notes = notes;
	}

	@PostMapping
	ResponseEntity<NoteResponse> create(@RequestBody CreateNoteRequest request) {
		Note saved = this.notes.save(new Note(request.body()));
		return ResponseEntity.created(URI.create("/notes/" + saved.getId())).body(NoteResponse.from(saved));
	}

	@GetMapping
	List<NoteResponse> findAll() {
		return this.notes.findAll().stream().map(NoteResponse::from).toList();
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@PathVariable Long id) {
		if (!this.notes.existsById(id)) {
			return ResponseEntity.notFound().build();
		}
		this.notes.deleteById(id);
		return ResponseEntity.noContent().build();
	}

	record CreateNoteRequest(String body) {
	}

	record NoteResponse(Long id, String body, Instant createdAt, Instant updatedAt, String createdBy, String updatedBy) {

		static NoteResponse from(Note note) {
			return new NoteResponse(note.getId(), note.getBody(), note.getCreatedAt(), note.getUpdatedAt(),
					note.getCreatedBy(), note.getUpdatedBy());
		}

	}

}
