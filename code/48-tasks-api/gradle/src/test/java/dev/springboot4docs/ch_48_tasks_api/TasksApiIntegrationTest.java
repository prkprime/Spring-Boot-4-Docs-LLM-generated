package dev.springboot4docs.ch_48_tasks_api;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import({ TestcontainersConfiguration.class, TasksApiIntegrationTest.TestJwtDecoderConfig.class })
class TasksApiIntegrationTest {

    @Autowired
    private RestTestClient rest;

    @Autowired
    private TaskRepository tasks;

    @BeforeEach
    void deleteTasks() {
        this.tasks.deleteAll();
    }

    @Test
    void anonymousRequestsAreRejected() {
        this.rest.get().uri("/api/tasks")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void readScopeCanListButCannotCreate() {
        this.rest.get().uri("/api/tasks")
                .header("Authorization", "Bearer read-token")
                .exchange()
                .expectStatus().isOk();

        this.rest.post().uri("/api/tasks")
                .header("Authorization", "Bearer read-token")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"title":"ship capstone","details":"wire REST, JWT, Postgres, Docker, and Fly"}
                        """)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void writeScopeCreatesAndCompletesTaskForCurrentSubject() {
        Long taskId = this.rest.post().uri("/api/tasks")
                .header("Authorization", "Bearer write-token")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"title":"ship capstone","details":"wire REST, JWT, Postgres, Docker, and Fly"}
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(TaskController.TaskResponse.class)
                .returnResult()
                .getResponseBody()
                .id();

        this.rest.get().uri("/api/tasks")
                .header("Authorization", "Bearer write-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo(taskId)
                .jsonPath("$[0].status").isEqualTo("OPEN");

        this.rest.patch().uri("/api/tasks/{id}/complete", taskId)
                .header("Authorization", "Bearer write-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("DONE")
                .jsonPath("$.completedAt").exists();
    }

    @Test
    void usersCannotSeeEachOthersTasks() {
        this.rest.post().uri("/api/tasks")
                .header("Authorization", "Bearer write-token")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"title":"alice only","details":"belongs to alice"}
                        """)
                .exchange()
                .expectStatus().isCreated();

        this.rest.get().uri("/api/tasks")
                .header("Authorization", "Bearer bob-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(0);
    }

    @TestConfiguration
    static class TestJwtDecoderConfig {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> switch (token) {
                case "read-token" -> jwt("alice", "tasks.read");
                case "write-token" -> jwt("alice", "tasks.read tasks.write");
                case "bob-token" -> jwt("bob", "tasks.read tasks.write");
                default -> throw new IllegalArgumentException("unknown test token");
            };
        }

        private static Jwt jwt(String subject, String scope) {
            Instant now = Instant.now();
            return Jwt.withTokenValue("test-token")
                    .header("alg", "none")
                    .subject(subject)
                    .issuedAt(now)
                    .expiresAt(now.plusSeconds(300))
                    .claim("scope", scope)
                    .build();
        }

    }

}
