package dev.springboot4docs.ch_19_relationships;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "book")
public class Book {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String title;

	private Integer publicationYear;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "author_id", nullable = false)
	private Author author;

	protected Book() {
	}

	public Book(String title, Integer publicationYear) {
		this.title = title;
		this.publicationYear = publicationYear;
	}

	public Long getId() {
		return this.id;
	}

	public String getTitle() {
		return this.title;
	}

	public Integer getPublicationYear() {
		return this.publicationYear;
	}

	public Author getAuthor() {
		return this.author;
	}

	void setAuthor(Author author) {
		this.author = author;
	}

}
