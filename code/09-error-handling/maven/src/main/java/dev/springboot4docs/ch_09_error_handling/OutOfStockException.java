package dev.springboot4docs.ch_09_error_handling;

public class OutOfStockException extends RuntimeException {

	private final String sku;

	public OutOfStockException(String sku) {
		super("SKU " + sku + " is out of stock");
		this.sku = sku;
	}

	public String getSku() {
		return this.sku;
	}

}
