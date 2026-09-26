package com.fahim.orderservice.client;

import com.fahim.orderservice.exception.ProductNotFoundException;
import com.fahim.orderservice.exception.ProductServiceUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Calls Product Service over plain HTTP with no credentials — v1 treats the internal network as
 * trusted. This is the flagged retrofit point for project-ideas #03 (service-to-service security).
 */
@Component
public class ProductClient {

    private final RestClient restClient;

    public ProductClient(RestClient productRestClient) {
        this.restClient = productRestClient;
    }

    public ProductSnapshot fetchProduct(Long productId) {
        try {
            return restClient
                    .get()
                    .uri("/products/{id}", productId)
                    .retrieve()
                    .onStatus(
                            HttpStatusCode::isError,
                            (request, response) -> {
                                if (response.getStatusCode().value()
                                        == HttpStatus.NOT_FOUND.value()) {
                                    throw new ProductNotFoundException(productId);
                                }
                                throw new ProductServiceUnavailableException(
                                        productId, response.getStatusCode().value());
                            })
                    .body(ProductSnapshot.class);
        } catch (ResourceAccessException ex) {
            throw new ProductServiceUnavailableException(productId, ex);
        }
    }
}
