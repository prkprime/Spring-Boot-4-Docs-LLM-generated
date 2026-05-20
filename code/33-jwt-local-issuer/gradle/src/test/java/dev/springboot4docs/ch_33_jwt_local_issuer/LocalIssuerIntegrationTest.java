package dev.springboot4docs.ch_33_jwt_local_issuer;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class LocalIssuerIntegrationTest {

    @Autowired
    private RestTestClient restTestClient;

    @Test
    void tokenCanBeUsedAtProtectedApi() {
        String accessToken = requestAccessToken();

        this.restTestClient.get().uri("/api/me")
                .headers((headers) -> headers.setBearerAuth(accessToken))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.subject").isEqualTo("demo-client")
                .jsonPath("$.scopes[0]").isEqualTo("read");
    }

    @Test
    void protectedApiRejectsAnonymousRequests() {
        this.restTestClient.get().uri("/api/me")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void publicApiAllowsAnonymousRequests() {
        this.restTestClient.get().uri("/api/public")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("public");
    }

    private String requestAccessToken() {
        TokenResponse tokenResponse = this.restTestClient.post().uri("/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("client_id=demo-client&client_secret=demo-secret&scope=read")
                .exchange()
                .expectStatus().isOk()
                .expectBody(TokenResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(tokenResponse).isNotNull();
        assertThat(tokenResponse.accessToken()).isNotBlank();
        return tokenResponse.accessToken();
    }

    record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") long expiresIn,
            String scope) {
    }

}
