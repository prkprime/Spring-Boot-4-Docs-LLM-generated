package dev.springboot4docs.ch_43_testing_testcontainers;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;

@Entity
public class Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String email;

	@Column(nullable = false)
	private Instant createdAt;

	protected Customer() {
	}

	public Customer(String email) {
		this.email = email;
	}

	@PrePersist
	void setCreatedAt() {
		if (this.createdAt == null) {
			this.createdAt = Instant.now();
		}
	}

	public Long getId() {
		return this.id;
	}

	public String getEmail() {
		return this.email;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

}
