package dev.springboot4docs.ch_07_json_jackson;

import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(OrderController.class)
@Import(JacksonConfig.class)
class OrderControllerWebMvcTest {

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private OrderSamples orderSamples;

	@Test
	void getOrderWritesJacksonJsonShape() {
		when(this.orderSamples.sample(99L)).thenReturn(sampleOrder());

		this.mvc.get().uri("/orders/99")
				.assertThat()
				.hasStatusOk()
				.hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.bodyJson()
				.extractingPath("$.placedAt").asString().isEqualTo("2026-05-09T10:15:30Z");

		this.mvc.get().uri("/orders/99")
				.assertThat()
				.bodyJson()
				.extractingPath("$.deliverBy").asString().isEqualTo("2026-05-12");

		this.mvc.get().uri("/orders/99")
				.assertThat()
				.bodyJson()
				.extractingPath("$.total").asString().isEqualTo("12.50 USD");

		this.mvc.get().uri("/orders/99")
				.assertThat()
				.bodyJson()
				.extractingPath("$.status").asString().isEqualTo("PAID");
	}

	@Test
	void postOrderReadsMoneyStringAndEchoesOrder() {
		String json = """
				{
				  "id": 99,
				  "placedAt": "2026-05-09T10:15:30Z",
				  "deliverBy": "2026-05-12",
				  "total": "12.50 USD",
				  "status": "PAID",
				  "items": [
				    { "sku": "coffee-250g", "qty": 2 },
				    { "sku": "filter-100", "qty": 1 }
				  ]
				}
				""";

		this.mvc.post().uri("/orders")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json)
				.assertThat()
				.hasStatusOk()
				.hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.bodyJson()
				.extractingPath("$.total").asString().isEqualTo("12.50 USD");
	}

	private static Order sampleOrder() {
		return new Order(99L, Instant.parse("2026-05-09T10:15:30Z"), LocalDate.parse("2026-05-12"),
				new Money(new BigDecimal("12.50"), "USD"), OrderStatus.PAID,
				List.of(new Item("coffee-250g", 2), new Item("filter-100", 1)));
	}

}
