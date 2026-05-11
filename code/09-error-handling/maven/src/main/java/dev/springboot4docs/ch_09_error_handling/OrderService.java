package dev.springboot4docs.ch_09_error_handling;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

@Service
public class OrderService {

	private final AtomicLong nextId = new AtomicLong(100);

	private final Map<Long, Order> orders = Map.of(
			1L, new Order(1L, "COFFEE-250G", 2, "ava@example.com"),
			2L, new Order(2L, "FILTER-100", 1, "sam@example.com"));

	public Order get(long id) {
		Order order = this.orders.get(id);
		if (order == null) {
			throw new OrderNotFoundException(id);
		}
		return order;
	}

	public Order place(OrderRequest request) {
		if ("SOLDOUT".equalsIgnoreCase(request.sku())) {
			throw new OutOfStockException(request.sku());
		}
		return new Order(this.nextId.incrementAndGet(), request.sku(), request.qty(), request.customerEmail());
	}

}
