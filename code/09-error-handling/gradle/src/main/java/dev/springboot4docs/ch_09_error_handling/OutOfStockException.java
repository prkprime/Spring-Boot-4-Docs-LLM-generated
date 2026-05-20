package dev.springboot4docs.ch_09_error_handling;

import lombok.Getter;

@Getter
public class OutOfStockException extends RuntimeException {

    private final String sku;

    public OutOfStockException(String sku) {
        super("SKU " + sku + " is out of stock");
        this.sku = sku;
    }

}
