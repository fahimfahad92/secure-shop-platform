package com.fahim.orderservice.exception;

public class ProductServiceUnavailableException extends RuntimeException {

    public ProductServiceUnavailableException(Long productId, int statusCode) {
        super("Product Service returned " + statusCode + " while looking up product " + productId);
    }

    public ProductServiceUnavailableException(Long productId, Throwable cause) {
        super("Product Service is unreachable while looking up product " + productId, cause);
    }
}
