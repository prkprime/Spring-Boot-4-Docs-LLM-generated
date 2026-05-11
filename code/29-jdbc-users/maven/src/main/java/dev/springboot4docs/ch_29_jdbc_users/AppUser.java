package dev.springboot4docs.ch_29_jdbc_users;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
class AppUser {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String username;

	@Column(nullable = false)
	private String passwordHash;

	@Column(nullable = false)
	private String roles;

	@Column(nullable = false)
	private boolean enabled = true;

	@Column(nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	protected AppUser() {
	}

	AppUser(String username, String passwordHash, String roles) {
		this.username = username;
		this.passwordHash = passwordHash;
		this.roles = roles;
	}

	Long getId() {
		return this.id;
	}

	String getUsername() {
		return this.username;
	}

	String getPasswordHash() {
		return this.passwordHash;
	}

	void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	String getRoles() {
		return this.roles;
	}

	boolean isEnabled() {
		return this.enabled;
	}

	Instant getCreatedAt() {
		return this.createdAt;
	}

}
