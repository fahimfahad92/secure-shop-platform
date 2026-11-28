package com.fahim.orderservice.exception;

/**
 * The order's product has been removed from the catalog since the order was placed, so its quantity
 * can't be changed. A 409, not the 400 of an unknown product on create: the request is well-formed,
 * the order's state is what prevents it.
 */
public class ProductNoLongerAvailableException extends RuntimeException {

    public ProductNoLongerAvailableException(Long productId) {
        super("Product " + productId + " is no longer available");
    }
}
