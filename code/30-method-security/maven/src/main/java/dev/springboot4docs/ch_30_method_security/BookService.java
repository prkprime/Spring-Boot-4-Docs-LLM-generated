package dev.springboot4docs.ch_30_method_security;

import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.security.RolesAllowed;

import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.prepost.PreFilter;
import org.springframework.stereotype.Service;

@Service
class BookService {

	private final List<Book> books = new ArrayList<>(List.of(
			new Book(1L, "Spring for Alice", "alice"),
			new Book(2L, "Spring for Bob", "bob"),
			new Book(3L, "Boot for Alice", "alice"),
			new Book(4L, "Security for Carol", "carol")));

	private final List<Book> imported = new ArrayList<>();

	@PreAuthorize("hasRole('ADMIN')")
	void deleteBook(Long id) {
		this.books.removeIf((book) -> book.id().equals(id));
	}

	@PreAuthorize("hasAuthority('SCOPE_book:write')")
	void writeBook(Book book) {
		this.books.add(book);
	}

	@PostAuthorize("returnObject.owner == authentication.name")
	Book getBook(Long id) {
		return this.books.stream()
				.filter((book) -> book.id().equals(id))
				.findFirst()
				.orElseThrow();
	}

	@PreFilter("filterObject.owner == authentication.name")
	void importBooks(List<Book> books) {
		this.imported.addAll(books);
	}

	@PostFilter("filterObject.owner == authentication.name")
	List<Book> findAll() {
		return new ArrayList<>(this.books);
	}

	@Secured("ROLE_AUDITOR")
	List<AuditEvent> auditLog() {
		return List.of(
				new AuditEvent(1L, "book catalog exported"),
				new AuditEvent(2L, "book deleted"));
	}

	@RolesAllowed({ "ADMIN", "AUDITOR" })
	void exportBooks() {
	}

	int importedCount() {
		return this.imported.size();
	}

}
