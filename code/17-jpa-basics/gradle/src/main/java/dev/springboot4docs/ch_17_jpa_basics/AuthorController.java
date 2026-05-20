package dev.springboot4docs.ch_17_jpa_basics;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class AuthorController {

    private final AuthorService authorService;

    public AuthorController(AuthorService authorService) {
        this.authorService = authorService;
    }

    @GetMapping("/authors")
    public List<Author> list() {
        return this.authorService.findAll();
    }

    @GetMapping("/authors/{id}")
    public ResponseEntity<Author> get(@PathVariable Long id) {
        return this.authorService.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/authors")
    public ResponseEntity<Author> create(@RequestBody AuthorRequest request) {
        Author saved = this.authorService.create(request.name(), request.email());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(saved.getId())
                .toUri();
        return ResponseEntity.created(location).body(saved);
    }

    @DeleteMapping("/authors/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        this.authorService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    public record AuthorRequest(String name, String email) {
    }

}
