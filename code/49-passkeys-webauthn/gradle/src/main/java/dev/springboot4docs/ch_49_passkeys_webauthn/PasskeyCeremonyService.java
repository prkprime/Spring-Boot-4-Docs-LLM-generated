package dev.springboot4docs.ch_49_passkeys_webauthn;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.yubico.webauthn.data.AttestationConveyancePreference;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import com.yubico.webauthn.data.PublicKeyCredentialParameters;
import com.yubico.webauthn.data.PublicKeyCredentialRequestOptions;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.data.UserVerificationRequirement;
import com.yubico.webauthn.data.exception.Base64UrlException;
import jakarta.transaction.Transactional;

import org.springframework.stereotype.Service;

@Service
class PasskeyCeremonyService {

    private static final int CHALLENGE_BYTES = 32;

    private final PasskeyProperties properties;

    private final PasskeyAccountRepository accounts;

    private final CredentialRecordRepository credentials;

    private final PasskeyChallengeRepository challenges;

    private final SecureRandom random = new SecureRandom();

    private final Clock clock;

    PasskeyCeremonyService(PasskeyProperties properties, PasskeyAccountRepository accounts,
            CredentialRecordRepository credentials, PasskeyChallengeRepository challenges, Clock clock) {
        this.properties = properties;
        this.accounts = accounts;
        this.credentials = credentials;
        this.challenges = challenges;
        this.clock = clock;
    }

    @Transactional
    PasskeyController.CeremonyOptions startRegistration(String username, String displayName, String origin) {
        requireAllowedOrigin(origin);
        if (this.accounts.findByUsername(username).isPresent()) {
            throw new PasskeyException("Username already exists");
        }
        Instant now = Instant.now(this.clock);
        ByteArray challenge = randomByteArray(CHALLENGE_BYTES);
        PasskeyChallenge stored = this.challenges.save(new PasskeyChallenge(
                challenge.getBase64Url(), username, ChallengePurpose.REGISTRATION, now,
                now.plus(this.properties.getChallengeTtl())));

        PublicKeyCredentialCreationOptions options = PublicKeyCredentialCreationOptions.builder()
                .rp(relyingParty())
                .user(UserIdentity.builder()
                        .name(username)
                        .displayName(displayName)
                        .id(userHandle(username))
                        .build())
                .challenge(challenge)
                .pubKeyCredParams(List.of(PublicKeyCredentialParameters.ES256, PublicKeyCredentialParameters.RS256))
                .timeout(this.properties.getChallengeTtl().toMillis())
                .attestation(AttestationConveyancePreference.NONE)
                .build();

        return new PasskeyController.CeremonyOptions(stored.getChallenge(), stored.getExpiresAt(), createJson(options));
    }

    @Transactional
    PasskeyController.RegistrationResult finishRegistration(PasskeyController.RegistrationFinishRequest request) {
        requireAllowedOrigin(request.origin());
        requireBase64Url(request.credentialId(), "credentialId");
        requireBase64Url(request.publicKeyCose(), "publicKeyCose");
        PasskeyChallenge challenge = requireChallenge(request.challenge(), request.username(), ChallengePurpose.REGISTRATION);
        if (this.credentials.existsByCredentialId(request.credentialId())) {
            throw new PasskeyException("Credential is already registered");
        }

        Instant now = Instant.now(this.clock);
        PasskeyAccount account = this.accounts.save(new PasskeyAccount(
                request.username(), request.username(), userHandle(request.username()).getBase64Url(), now));
        this.credentials.save(new CredentialRecord(account, request.credentialId(), request.publicKeyCose(),
                request.signCount(), now));
        challenge.consume();

        return new PasskeyController.RegistrationResult(account.getUsername(), request.credentialId(),
                List.of("recovery-code", "verified-email"));
    }

    @Transactional
    PasskeyController.CeremonyOptions startAuthentication(String username, String origin) {
        requireAllowedOrigin(origin);
        this.accounts.findByUsername(username).orElseThrow(() -> new PasskeyException("Unknown username"));
        List<CredentialRecord> accountCredentials = this.credentials.findByAccountUsername(username);
        if (accountCredentials.isEmpty()) {
            throw new PasskeyException("Account has no registered passkeys");
        }

        Instant now = Instant.now(this.clock);
        ByteArray challenge = randomByteArray(CHALLENGE_BYTES);
        PasskeyChallenge stored = this.challenges.save(new PasskeyChallenge(
                challenge.getBase64Url(), username, ChallengePurpose.AUTHENTICATION, now,
                now.plus(this.properties.getChallengeTtl())));

        PublicKeyCredentialRequestOptions options = PublicKeyCredentialRequestOptions.builder()
                .challenge(challenge)
                .rpId(this.properties.getRelyingPartyId())
                .allowCredentials(accountCredentials.stream()
                        .map((credential) -> PublicKeyCredentialDescriptor.builder()
                                .id(byteArrayFromBase64Url(credential.getCredentialId(), "stored credentialId"))
                                .build())
                        .toList())
                .timeout(this.properties.getChallengeTtl().toMillis())
                .userVerification(UserVerificationRequirement.PREFERRED)
                .build();

        return new PasskeyController.CeremonyOptions(stored.getChallenge(), stored.getExpiresAt(), getJson(options));
    }

    @Transactional
    PasskeyController.AuthenticationResult finishAuthentication(PasskeyController.AuthenticationFinishRequest request) {
        requireAllowedOrigin(request.origin());
        PasskeyChallenge challenge = requireChallenge(request.challenge(), request.username(), ChallengePurpose.AUTHENTICATION);
        CredentialRecord credential = this.credentials.findByCredentialId(request.credentialId())
                .orElseThrow(() -> new PasskeyException("Unknown credential"));
        if (!credential.getAccount().getUsername().equals(request.username())) {
            throw new PasskeyException("Credential does not belong to username");
        }
        if (request.signCount() != 0 && request.signCount() <= credential.getSignCount()) {
            throw new PasskeyException("Authenticator sign count did not increase");
        }
        credential.updateSignCount(request.signCount());
        challenge.consume();
        return new PasskeyController.AuthenticationResult(request.username(), request.credentialId(), true);
    }

    private PasskeyChallenge requireChallenge(String challenge, String username, ChallengePurpose purpose) {
        PasskeyChallenge stored = this.challenges.findById(challenge)
                .orElseThrow(() -> new PasskeyException("Unknown challenge"));
        if (stored.isConsumed()) {
            throw new PasskeyException("Challenge was already used");
        }
        if (stored.isExpired(Instant.now(this.clock))) {
            throw new PasskeyException("Challenge expired");
        }
        if (!stored.getUsername().equals(username) || stored.getPurpose() != purpose) {
            throw new PasskeyException("Challenge does not match this ceremony");
        }
        return stored;
    }

    private void requireAllowedOrigin(String origin) {
        if (!this.properties.getAllowedOrigins().contains(origin)) {
            throw new PasskeyException("Origin is not allowed for this relying party");
        }
    }

    private RelyingPartyIdentity relyingParty() {
        return RelyingPartyIdentity.builder()
                .id(this.properties.getRelyingPartyId())
                .name(this.properties.getRelyingPartyName())
                .build();
    }

    private ByteArray randomByteArray(int size) {
        byte[] bytes = new byte[size];
        this.random.nextBytes(bytes);
        return new ByteArray(bytes);
    }

    private ByteArray userHandle(String username) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return new ByteArray(digest.digest(username.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by the JDK", ex);
        }
    }

    private void requireBase64Url(String value, String field) {
        byteArrayFromBase64Url(value, field);
    }

    private ByteArray byteArrayFromBase64Url(String value, String field) {
        try {
            return ByteArray.fromBase64Url(value);
        }
        catch (Base64UrlException ex) {
            throw new PasskeyException(field + " must be base64url encoded");
        }
    }

    private String createJson(PublicKeyCredentialCreationOptions options) {
        try {
            return options.toCredentialsCreateJson();
        }
        catch (Exception ex) {
            throw new IllegalStateException("Could not serialize registration options", ex);
        }
    }

    private String getJson(PublicKeyCredentialRequestOptions options) {
        try {
            return options.toCredentialsGetJson();
        }
        catch (Exception ex) {
            throw new IllegalStateException("Could not serialize authentication options", ex);
        }
    }

}
