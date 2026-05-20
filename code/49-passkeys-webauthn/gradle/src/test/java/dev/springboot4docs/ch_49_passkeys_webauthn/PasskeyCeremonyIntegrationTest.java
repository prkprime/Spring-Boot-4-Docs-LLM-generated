package dev.springboot4docs.ch_49_passkeys_webauthn;

import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class PasskeyCeremonyIntegrationTest {

    private static final String ORIGIN = "http://localhost:8080";

    @Autowired
    private RestTestClient rest;

    @Autowired
    private CredentialRecordRepository credentials;

    @Autowired
    private PasskeyChallengeRepository challenges;

    @Autowired
    private PasskeyAccountRepository accounts;

    @BeforeEach
    void deleteData() {
        this.credentials.deleteAll();
        this.challenges.deleteAll();
        this.accounts.deleteAll();
    }

    @Test
    void registrationOptionsContainServerChallengeAndRelyingParty() {
        PasskeyController.CeremonyOptions options = startRegistration("alice");

        this.rest.post().uri("/api/passkeys/register/finish")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {
                          "username": "alice",
                          "origin": "http://localhost:8080",
                          "challenge": "%s",
                          "credentialId": "%s",
                          "publicKeyCose": "%s",
                          "signCount": 1
                        }
                        """.formatted(options.challenge(), base64Url("alice-credential"), base64Url("alice-public-key")))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.username").isEqualTo("alice")
                .jsonPath("$.credentialId").isEqualTo(base64Url("alice-credential"))
                .jsonPath("$.recoveryMethods[0]").isEqualTo("recovery-code");

        this.rest.post().uri("/api/passkeys/register/finish")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {
                          "username": "alice",
                          "origin": "http://localhost:8080",
                          "challenge": "%s",
                          "credentialId": "%s",
                          "publicKeyCose": "%s",
                          "signCount": 1
                        }
                        """.formatted(options.challenge(), base64Url("second-credential"), base64Url("second-public-key")))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.title").isEqualTo("Passkey ceremony rejected")
                .jsonPath("$.detail").isEqualTo("Challenge was already used");
    }

    @Test
    void originMustMatchConfiguredRelyingParty() {
        this.rest.post().uri("/api/passkeys/register/options")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"username":"mallory","displayName":"Mallory","origin":"https://evil.example"}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.detail").isEqualTo("Origin is not allowed for this relying party");
    }

    @Test
    void authenticationRequiresExistingCredentialAndIncreasingSignCount() {
        PasskeyController.CeremonyOptions registration = startRegistration("alice");
        String credentialId = base64Url("alice-credential");
        finishRegistration("alice", registration.challenge(), credentialId, 10);

        PasskeyController.CeremonyOptions authentication = startAuthentication("alice");

        this.rest.post().uri("/api/passkeys/authenticate/finish")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {
                          "username": "alice",
                          "origin": "http://localhost:8080",
                          "challenge": "%s",
                          "credentialId": "%s",
                          "signCount": 11
                        }
                        """.formatted(authentication.challenge(), credentialId))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.username").isEqualTo("alice")
                .jsonPath("$.signedIn").isEqualTo(true);

        PasskeyController.CeremonyOptions replay = startAuthentication("alice");
        this.rest.post().uri("/api/passkeys/authenticate/finish")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {
                          "username": "alice",
                          "origin": "http://localhost:8080",
                          "challenge": "%s",
                          "credentialId": "%s",
                          "signCount": 10
                        }
                        """.formatted(replay.challenge(), credentialId))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.detail").isEqualTo("Authenticator sign count did not increase");
    }

    @Test
    void usersCannotAuthenticateWithAnotherUsersCredential() {
        PasskeyController.CeremonyOptions aliceRegistration = startRegistration("alice");
        String credentialId = base64Url("alice-credential");
        finishRegistration("alice", aliceRegistration.challenge(), credentialId, 1);

        PasskeyController.CeremonyOptions bobRegistration = startRegistration("bob");
        finishRegistration("bob", bobRegistration.challenge(), base64Url("bob-credential"), 1);
        PasskeyController.CeremonyOptions bobAuthentication = startAuthentication("bob");

        this.rest.post().uri("/api/passkeys/authenticate/finish")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {
                          "username": "bob",
                          "origin": "http://localhost:8080",
                          "challenge": "%s",
                          "credentialId": "%s",
                          "signCount": 2
                        }
                        """.formatted(bobAuthentication.challenge(), credentialId))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.detail").isEqualTo("Credential does not belong to username");
    }

    private PasskeyController.CeremonyOptions startRegistration(String username) {
        return this.rest.post().uri("/api/passkeys/register/options")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"username":"%s","displayName":"%s","origin":"http://localhost:8080"}
                        """.formatted(username, username))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(PasskeyController.CeremonyOptions.class)
                .returnResult()
                .getResponseBody();
    }

    private PasskeyController.CeremonyOptions startAuthentication(String username) {
        return this.rest.post().uri("/api/passkeys/authenticate/options")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"username":"%s","origin":"http://localhost:8080"}
                        """.formatted(username))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(PasskeyController.CeremonyOptions.class)
                .returnResult()
                .getResponseBody();
    }

    private void finishRegistration(String username, String challenge, String credentialId, long signCount) {
        this.rest.post().uri("/api/passkeys/register/finish")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {
                          "username": "%s",
                          "origin": "http://localhost:8080",
                          "challenge": "%s",
                          "credentialId": "%s",
                          "publicKeyCose": "%s",
                          "signCount": %d
                        }
                        """.formatted(username, challenge, credentialId, base64Url(username + "-public-key"), signCount))
                .exchange()
                .expectStatus().isOk();
    }

    private static String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

}
