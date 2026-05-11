package dev.springboot4docs.ch_17_jpa_basics;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Author {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 120)
	private String name;

	@Column(nullable = false, unique = true)
	private String email;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public Author(String name, String email) {
		this.name = name;
		this.email = email;
	}

	@PrePersist
	void prePersist() {
		this.createdAt = Instant.now();
	}

}
