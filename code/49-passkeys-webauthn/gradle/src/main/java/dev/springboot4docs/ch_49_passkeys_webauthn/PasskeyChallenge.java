package dev.springboot4docs.ch_49_passkeys_webauthn;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    PasskeyChallenge(String challenge, String username, ChallengePurpose purpose, Instant createdAt, Instant expiresAt) {
        this.challenge = challenge;
        this.username = username;
        this.purpose = purpose;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    void consume() {
        this.consumed = true;
    }

    boolean isExpired(Instant now) {
        return !this.expiresAt.isAfter(now);
    }

}
