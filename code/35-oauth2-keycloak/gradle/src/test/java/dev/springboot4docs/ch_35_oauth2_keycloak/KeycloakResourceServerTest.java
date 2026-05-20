package dev.springboot4docs.ch_35_oauth2_keycloak;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class KeycloakResourceServerTest {

    private static final String CLIENT_ID = "spring-boot-app";
    private static final String REALM = "sb4-docs";

    @LocalServerPort
    private int port;

    private final RestClient restClient = RestClient.create();

    @DynamicPropertySource
    static void keycloakProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> TestcontainersConfiguration.issuerUri(REALM));
    }

    @Test
    void meEndpointAcceptsTokenFromKeycloak() {
        String token = accessToken("alice", "secret");

        Map<String, Object> response = get("/api/me", token, new ParameterizedTypeReference<>() {
        });

        assertThat(response).containsEntry("username", "alice");
    }

    @Test
    void adminEndpointRejectsAlice() {
        String token = accessToken("alice", "secret");

        assertThat(status("/api/admin/secrets", token).value()).isEqualTo(403);
    }

    @Test
    void adminEndpointAcceptsBob() {
        String token = accessToken("bob", "secret");

        String response = get("/api/admin/secrets", token, String.class);

        assertThat(response).isEqualTo("admin secrets");
    }

    private String accessToken(String username, String password) {
        TokenResponse response = this.restClient.post()
                .uri(TestcontainersConfiguration.KEYCLOAK.getAuthServerUrl()
                        + "/realms/{realm}/protocol/openid-connect/token", REALM)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=password&client_id=" + CLIENT_ID
                        + "&username=" + username
                        + "&password=" + password)
                .retrieve()
                .body(TokenResponse.class);

        assertThat(response).isNotNull();
        return response.access_token();
    }

    private <T> T get(String path, String token, Class<T> bodyType) {
        return this.restClient.get()
                .uri(java.net.URI.create("http://localhost:" + this.port + path))
                .headers((headers) -> headers.setBearerAuth(token))
                .retrieve()
                .body(bodyType);
    }

    private <T> T get(String path, String token, ParameterizedTypeReference<T> bodyType) {
        return this.restClient.get()
                .uri(java.net.URI.create("http://localhost:" + this.port + path))
                .headers((headers) -> headers.setBearerAuth(token))
                .retrieve()
                .body(bodyType);
    }

    private HttpStatusCode status(String path, String token) {
        return this.restClient.get()
                .uri(java.net.URI.create("http://localhost:" + this.port + path))
                .headers((headers) -> headers.setBearerAuth(token))
                .exchange((request, response) -> response.getStatusCode());
    }

    record TokenResponse(String access_token) {
    }

}
