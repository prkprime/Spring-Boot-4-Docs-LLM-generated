package dev.springboot4docs.ch_49_passkeys_webauthn;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;

@Entity
class PasskeyChallenge {

	@Id
	@Column(length = 86)
	private String challenge;

	@Column(nullable = false, length = 128)
	private String username;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private ChallengePurpose purpose;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant expiresAt;

	@Column(nullable = false)
	private boolean consumed;

	protected PasskeyChallenge() {
	}

	PasskeyChallenge(String challenge, String username, ChallengePurpose purpose, Instant createdAt, Instant expiresAt) {
		this.challenge = challenge;
		this.username = username;
		this.purpose = purpose;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	String getChallenge() {
		return this.challenge;
	}

	String getUsername() {
		return this.username;
	}

	ChallengePurpose getPurpose() {
		return this.purpose;
	}

	Instant getCreatedAt() {
		return this.createdAt;
	}

	Instant getExpiresAt() {
		return this.expiresAt;
	}

	boolean isConsumed() {
		return this.consumed;
	}

	void consume() {
		this.consumed = true;
	}

	boolean isExpired(Instant now) {
		return !this.expiresAt.isAfter(now);
	}

}
