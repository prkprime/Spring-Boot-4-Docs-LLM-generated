package dev.springboot4docs.ch_42_testing_slices;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/notes")
public class NoteController {

    private final NoteService notes;

    public NoteController(NoteService notes) {
        this.notes = notes;
    }

    @GetMapping
    List<NoteResponse> list() {
        return this.notes.list().stream().map(NoteResponse::from).toList();
    }

    @GetMapping("/{id}")
    NoteResponse get(@PathVariable long id) {
        return NoteResponse.from(this.notes.get(id));
    }

    @PostMapping
    ResponseEntity<NoteResponse> create(@RequestBody NoteRequest request) {
        Note note = this.notes.create(request.title(), request.body());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(note.getId())
            .toUri();
        return ResponseEntity.created(location).body(NoteResponse.from(note));
    }

    @PutMapping("/{id}")
    NoteResponse update(@PathVariable long id, @RequestBody NoteRequest request) {
        return NoteResponse.from(this.notes.update(id, request.title(), request.body()));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable long id) {
        this.notes.delete(id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(NoteNotFoundException.class)
    ResponseEntity<Void> noteNotFound() {
        return ResponseEntity.notFound().build();
    }

    public record NoteRequest(String title, String body) {
    }

    public record NoteResponse(Long id, String title, String body) {

        static NoteResponse from(Note note) {
            return new NoteResponse(note.getId(), note.getTitle(), note.getBody());
        }

    }

}
