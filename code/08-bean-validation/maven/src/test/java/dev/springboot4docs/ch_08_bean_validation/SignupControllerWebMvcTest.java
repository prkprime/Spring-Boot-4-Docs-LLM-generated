package dev.springboot4docs.ch_08_bean_validation;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(SignupController.class)
class SignupControllerWebMvcTest {

	@Autowired
	private MockMvcTester mvc;

	@Test
	void validSignupReturnsWelcomeMessage() {
		this.mvc.post().uri("/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "name": "Ava Patel",
						  "email": "ava@example.com",
						  "age": 31,
						  "phone": "+1 555 0101"
						}
						""")
				.assertThat()
				.hasStatusOk()
				.hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.bodyJson()
				.extractingPath("$.message")
				.asString()
				.contains("Ava Patel");
	}

	@Test
	void invalidSignupReturnsFieldErrors() {
		this.mvc.post().uri("/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "email": "not-an-email",
						  "age": 5,
						  "phone": "abc"
						}
						""")
				.assertThat()
				.hasStatus(400)
				.hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.bodyJson()
				.extractingPath("$.errors[*].field")
				.asArray()
				.contains("name", "email", "age", "phone");
	}

	@Test
	void weakPasswordReturnsFieldError() {
		this.mvc.post().uri("/password")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "newPassword": "password"
						}
						""")
				.assertThat()
				.hasStatus(400)
				.bodyJson()
				.extractingPath("$.errors[*].field")
				.asArray()
				.contains("newPassword");
	}

	@Test
	void strongPasswordReturnsNoContent() {
		this.mvc.post().uri("/password")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "newPassword": "Correct99!"
						}
						""")
				.assertThat()
				.hasStatus(204);
	}

}
