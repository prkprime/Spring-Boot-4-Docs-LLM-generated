package dev.springboot4docs.ch_30_method_security;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class BookController {

    private final BookService books;

    BookController(BookService books) {
        this.books = books;
    }

    @DeleteMapping("/books/{id}")
    Map<String, String> deleteBook(@PathVariable Long id) {
        this.books.deleteBook(id);
        return Map.of("status", "deleted");
    }

    @PostMapping("/books/write")
    Map<String, String> writeBook(@RequestBody Book book) {
        this.books.writeBook(book);
        return Map.of("status", "written");
    }

    @GetMapping("/books/{id}")
    Book getBook(@PathVariable Long id) {
        return this.books.getBook(id);
    }

    @PostMapping("/books/import")
    Map<String, Integer> importBooks(@RequestBody List<Book> books) {
        this.books.importBooks(books);
        return Map.of("imported", this.books.importedCount());
    }

    @GetMapping("/books")
    List<Book> findAll() {
        return this.books.findAll();
    }

    @GetMapping("/audit")
    List<AuditEvent> auditLog() {
        return this.books.auditLog();
    }

    @PostMapping("/books/export")
    Map<String, String> exportBooks() {
        this.books.exportBooks();
        return Map.of("status", "exported");
    }

}
