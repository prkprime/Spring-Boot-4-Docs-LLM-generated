package dev.springboot4docs.ch_49_passkeys_webauthn;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
class CredentialRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private PasskeyAccount account;

	@Column(nullable = false, unique = true, length = 512)
	private String credentialId;

	@Column(nullable = false, length = 4000)
	private String publicKeyCose;

	@Column(nullable = false)
	private long signCount;

	@Column(nullable = false)
	private Instant createdAt;

	protected CredentialRecord() {
	}

	CredentialRecord(PasskeyAccount account, String credentialId, String publicKeyCose, long signCount, Instant createdAt) {
		this.account = account;
		this.credentialId = credentialId;
		this.publicKeyCose = publicKeyCose;
		this.signCount = signCount;
		this.createdAt = createdAt;
	}

	Long getId() {
		return this.id;
	}

	PasskeyAccount getAccount() {
		return this.account;
	}

	String getCredentialId() {
		return this.credentialId;
	}

	String getPublicKeyCose() {
		return this.publicKeyCose;
	}

	long getSignCount() {
		return this.signCount;
	}

	void updateSignCount(long signCount) {
		this.signCount = signCount;
	}

	Instant getCreatedAt() {
		return this.createdAt;
	}

}
