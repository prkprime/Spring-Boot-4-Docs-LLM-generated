package dev.springboot4docs.ch_07_json_jackson;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

	private final OrderSamples orderSamples;

	public OrderController(OrderSamples orderSamples) {
		this.orderSamples = orderSamples;
	}

	@GetMapping("/orders/{id}")
	public Order get(@PathVariable Long id) {
		return this.orderSamples.sample(id);
	}

	@PostMapping("/orders")
	public Order create(@RequestBody Order order) {
		return order;
	}

}
