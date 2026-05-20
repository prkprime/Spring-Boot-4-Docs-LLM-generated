package dev.springboot4docs.ch_30_method_security;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(BookController.class)
@Import({ SecurityConfig.class, BookService.class })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MethodSecurityWebMvcTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void deleteBookRejectsUser() {
        this.mvc.delete().uri("/books/1")
                .assertThat()
                .hasStatus(403);
    }

    @Test
    @WithMockUser(username = "bob", roles = { "USER", "ADMIN" })
    void deleteBookAcceptsAdmin() {
        this.mvc.delete().uri("/books/1")
                .assertThat()
                .hasStatusOk();
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void getBookAcceptsOwner() {
        this.mvc.get().uri("/books/1")
                .assertThat()
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.owner")
                .asString()
                .isEqualTo("alice");
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void getBookRejectsNonOwnerAfterLoadingTheBook() {
        this.mvc.get().uri("/books/2")
                .assertThat()
                .hasStatus(403);
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void findAllFiltersBooksOwnedByOtherUsers() {
        this.mvc.get().uri("/books")
                .assertThat()
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.length()")
                .asNumber()
                .isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "carol", roles = { "USER", "AUDITOR" })
    void auditLogAcceptsAuditor() {
        this.mvc.get().uri("/audit")
                .assertThat()
                .hasStatusOk();
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void auditLogRejectsNonAuditor() {
        this.mvc.get().uri("/audit")
                .assertThat()
                .hasStatus(403);
    }

    @Test
    @WithMockUser(username = "bob", roles = { "USER", "ADMIN" })
    void exportBooksAcceptsAdmin() {
        this.mvc.post().uri("/books/export")
                .assertThat()
                .hasStatusOk();
    }

    @Test
    @WithMockUser(username = "carol", roles = { "USER", "AUDITOR" })
    void exportBooksAcceptsAuditor() {
        this.mvc.post().uri("/books/export")
                .assertThat()
                .hasStatusOk();
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void exportBooksRejectsUser() {
        this.mvc.post().uri("/books/export")
                .assertThat()
                .hasStatus(403);
    }

}
