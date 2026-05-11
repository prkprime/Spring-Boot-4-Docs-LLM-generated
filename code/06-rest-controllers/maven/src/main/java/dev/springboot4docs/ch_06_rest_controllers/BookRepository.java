package dev.springboot4docs.ch_06_rest_controllers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

@Component
public class BookRepository {

	private final ConcurrentMap<Long, Book> books = new ConcurrentHashMap<>();

	private final AtomicLong nextId = new AtomicLong();

	public List<Book> findAll() {
		return new ArrayList<>(this.books.values());
	}

	public Optional<Book> findById(Long id) {
		return Optional.ofNullable(this.books.get(id));
	}

	public Book create(Book book) {
		Long id = this.nextId.incrementAndGet();
		Book saved = new Book(id, book.title(), book.author());
		this.books.put(id, saved);
		return saved;
	}

	public Optional<Book> update(Long id, Book book) {
		if (!this.books.containsKey(id)) {
			return Optional.empty();
		}
		Book saved = new Book(id, book.title(), book.author());
		this.books.put(id, saved);
		return Optional.of(saved);
	}

	public void deleteById(Long id) {
		this.books.remove(id);
	}

	public List<Book> search(Optional<String> title, Optional<String> author) {
		return this.books.values().stream()
				.filter((book) -> title.map((value) -> containsIgnoreCase(book.title(), value)).orElse(true))
				.filter((book) -> author.map((value) -> containsIgnoreCase(book.author(), value)).orElse(true))
				.toList();
	}

	private boolean containsIgnoreCase(String text, String candidate) {
		if (text == null || candidate == null) {
			return false;
		}
		return text.toLowerCase(Locale.ROOT).contains(candidate.toLowerCase(Locale.ROOT));
	}

}
