package dev.springboot4docs.ch_26_fundamentals;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

@WebMvcTest(HelloController.class)
@Import(SecurityConfig.class)
class HelloControllerWebMvcTest {

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
    void meRequiresAuthentication() {
        this.mvc.get().uri("/me")
                .assertThat()
                .hasStatus(401);
    }

    @Test
    @WithMockUser("alice")
    void meUsesTheAuthenticatedPrincipalName() {
        this.mvc.get().uri("/me")
                .assertThat()
                .hasStatusOk()
                .bodyText()
                .isEqualTo("alice");
    }

    @Test
    void meAcceptsValidHttpBasicCredentials() {
        this.mvc.get().uri("/me")
                .with(httpBasic("alice", "secret"))
                .assertThat()
                .hasStatusOk()
                .bodyText()
                .isEqualTo("alice");
    }

    @Test
    void meRejectsInvalidHttpBasicCredentials() {
        this.mvc.get().uri("/me")
                .with(httpBasic("alice", "wrong"))
                .assertThat()
                .hasStatus(401);
    }

}
