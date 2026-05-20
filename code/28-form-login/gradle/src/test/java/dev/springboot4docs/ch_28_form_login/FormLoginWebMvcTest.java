package dev.springboot4docs.ch_28_form_login;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.logout;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;

@WebMvcTest(WebController.class)
@Import(SecurityConfig.class)
class FormLoginWebMvcTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void loginPageIsPublic() {
        this.mvc.get().uri("/login")
                .assertThat()
                .hasStatusOk()
                .hasViewName("login");
    }

    @Test
    void dashboardRedirectsAnonymousUsersToLogin() {
        this.mvc.get().uri("/dashboard")
                .assertThat()
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/login");
    }

    @Test
    void formLoginAuthenticatesAliceAndCreatesASession() {
        MvcTestResult result = this.mvc.perform(formLogin("/login")
                .user("alice")
                .password("secret"));

        result.assertThat()
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/dashboard")
                .matches(authenticated().withUsername("alice"));

        HttpSession session = result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getAttribute("SPRING_SECURITY_CONTEXT")).isNotNull();
        assertThat(session.getId()).isNotBlank();
    }

    @Test
    @WithMockUser("alice")
    void dashboardShowsThePrincipalName() {
        this.mvc.get().uri("/dashboard")
                .assertThat()
                .hasStatusOk()
                .bodyText()
                .contains("alice");
    }

    @Test
    @WithMockUser("alice")
    void logoutRedirectsToLoginWithLogoutFlag() {
        this.mvc.perform(logout())
                .assertThat()
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/login?logout");
    }

    @Test
    void dashboardWhenAnonymousRedirectsToLogin() {
        this.mvc.get().uri("/dashboard")
                .assertThat()
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/login");
    }

}
