package dev.springboot4docs.ch_19_relationships;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "author")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Author {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String name;

	private String email;

	@OneToMany(mappedBy = "author", cascade = CascadeType.ALL, orphanRemoval = true)
	@Getter(AccessLevel.NONE)
	private List<Book> books = new ArrayList<>();

	public Author(String name, String email) {
		this.name = name;
		this.email = email;
	}

	public List<Book> getBooks() {
		return Collections.unmodifiableList(this.books);
	}

	public void addBook(Book book) {
		this.books.add(book);
		book.setAuthor(this);
	}

}
