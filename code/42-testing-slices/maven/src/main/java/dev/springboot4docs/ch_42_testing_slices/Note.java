package dev.springboot4docs.ch_42_testing_slices;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Note {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String title;

	private String body;

	protected Note() {
	}

	public Note(String title, String body) {
		this.title = title;
		this.body = body;
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

	public void rename(String title, String body) {
		this.title = title;
		this.body = body;
	}

}
