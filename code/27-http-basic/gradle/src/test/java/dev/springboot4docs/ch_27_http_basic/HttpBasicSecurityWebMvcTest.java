package dev.springboot4docs.ch_27_http_basic;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

@WebMvcTest({ PublicController.class, ApiController.class, AdminController.class })
@Import(SecurityConfig.class)
class HttpBasicSecurityWebMvcTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void rootIsPublic() {
        this.mvc.get().uri("/")
                .assertThat()
                .hasStatusOk()
                .bodyText()
                .isEqualTo("public root");
    }

    @Test
    void publicInfoIsPublic() {
        this.mvc.get().uri("/public/info")
                .assertThat()
                .hasStatusOk()
                .bodyText()
                .isEqualTo("public info");
    }

    @Test
    void apiMeRequiresAuthentication() {
        this.mvc.get().uri("/api/me")
                .assertThat()
                .hasStatus(401);
    }

    @Test
    void apiMeAcceptsAliceWithHttpBasic() {
        this.mvc.get().uri("/api/me")
                .with(httpBasic("alice", "secret"))
                .assertThat()
                .hasStatusOk()
                .bodyText()
                .isEqualTo("alice");
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void adminSecretsRejectsAlice() {
        this.mvc.get().uri("/admin/secrets")
                .assertThat()
                .hasStatus(403);
    }

    @Test
    void adminSecretsAcceptsBob() {
        this.mvc.get().uri("/admin/secrets")
                .with(httpBasic("bob", "secret"))
                .assertThat()
                .hasStatusOk()
                .bodyText()
                .isEqualTo("admin secrets");
    }

}
