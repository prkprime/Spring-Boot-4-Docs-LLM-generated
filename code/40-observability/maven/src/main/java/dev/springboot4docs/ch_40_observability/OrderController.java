package dev.springboot4docs.ch_40_observability;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {

	private final OrderService orderService;

	public OrderController(OrderService orderService) {
		this.orderService = orderService;
	}

	@PostMapping
	public ResponseEntity<OrderReceipt> create(@RequestBody Order order) {
		OrderReceipt receipt = this.orderService.process(order);
		return ResponseEntity.created(URI.create("/orders/" + receipt.orderId())).body(receipt);
	}

}
