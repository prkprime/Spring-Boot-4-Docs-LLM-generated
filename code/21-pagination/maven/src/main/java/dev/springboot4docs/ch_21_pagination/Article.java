package dev.springboot4docs.ch_21_pagination;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Article {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String title;

	private String body;

	private Instant publishedAt;

	protected Article() {
	}

	public Article(String title, String body, Instant publishedAt) {
		this.title = title;
		this.body = body;
		this.publishedAt = publishedAt;
	}

	public Long getId() {
		return this.id;
	}

	public String getTitle() {
		return this.title;
	}

	public String getBody() {
		return this.body;
	}

	public Instant getPublishedAt() {
		return this.publishedAt;
	}

}
