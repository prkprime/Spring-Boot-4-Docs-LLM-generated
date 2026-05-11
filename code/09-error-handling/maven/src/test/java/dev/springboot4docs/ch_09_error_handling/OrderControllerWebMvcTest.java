package dev.springboot4docs.ch_09_error_handling;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(OrderController.class)
@Import({ OrderService.class, GlobalExceptionHandler.class })
class OrderControllerWebMvcTest {

	@Autowired
	private MockMvcTester mvc;

	@Test
	void validationErrorReturnsProblemDetailWithFieldErrors() {
		String invalidOrder = """
				{
				  "sku": "",
				  "qty": 0,
				  "customerEmail": "not-an-email"
				}
				""";

		this.mvc.post().uri("/orders")
				.contentType(MediaType.APPLICATION_JSON)
				.content(invalidOrder)
				.assertThat()
				.hasStatus(400)
				.hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.bodyJson()
				.extractingPath("$.errors.sku").asString().isNotBlank();

		this.mvc.post().uri("/orders")
				.contentType(MediaType.APPLICATION_JSON)
				.content(invalidOrder)
				.assertThat()
				.bodyJson()
				.extractingPath("$.errors.qty").asString().isNotBlank();

		this.mvc.post().uri("/orders")
				.contentType(MediaType.APPLICATION_JSON)
				.content(invalidOrder)
				.assertThat()
				.bodyJson()
				.extractingPath("$.errors.customerEmail").asString().isNotBlank();
	}

	@Test
	void missingOrderReturnsNotFoundProblemDetail() {
		this.mvc.get().uri("/orders/{id}", 99)
				.assertThat()
				.hasStatus(404)
				.hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.bodyJson()
				.extractingPath("$.type").asString().isEqualTo("https://api.example/errors/order-not-found");

		this.mvc.get().uri("/orders/{id}", 99)
				.assertThat()
				.bodyJson()
				.extractingPath("$.title").asString().isEqualTo("Not Found");

		this.mvc.get().uri("/orders/{id}", 99)
				.assertThat()
				.bodyJson()
				.extractingPath("$.detail").asString().isEqualTo("Order 99 was not found");

		this.mvc.get().uri("/orders/{id}", 99)
				.assertThat()
				.bodyJson()
				.extractingPath("$.instance").asString().isEqualTo("/orders/99");
	}

	@Test
	void outOfStockReturnsConflictProblemDetailWithSkuExtension() {
		this.mvc.post().uri("/orders")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "sku": "SOLDOUT",
						  "qty": 1,
						  "customerEmail": "ava@example.com"
						}
						""")
				.assertThat()
				.hasStatus(409)
				.hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.bodyJson()
				.extractingPath("$.sku").asString().isEqualTo("SOLDOUT");
	}

}
