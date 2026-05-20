package dev.springboot4docs.ch_49_passkeys_webauthn;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class PasskeyAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 128)
    private String username;

    @Column(nullable = false, length = 128)
    private String displayName;

    @Column(nullable = false, unique = true, length = 128)
    private String userHandle;

    @Column(nullable = false)
    private Instant createdAt;

    PasskeyAccount(String username, String displayName, String userHandle, Instant createdAt) {
        this.username = username;
        this.displayName = displayName;
        this.userHandle = userHandle;
        this.createdAt = createdAt;
    }

}
