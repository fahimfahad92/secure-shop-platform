package com.fahim.productservice.controller;

import com.fahim.productservice.dto.CreateProductRequest;
import com.fahim.productservice.dto.ProductResponse;
import com.fahim.productservice.dto.UpdateProductRequest;
import com.fahim.productservice.model.Product;
import com.fahim.productservice.service.ProductService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(
            @Valid @RequestBody CreateProductRequest request) {
        Product product = productService.create(request);
        return ResponseEntity.created(URI.create("/products/" + product.getId()))
                .body(ProductResponse.from(product));
    }

    @GetMapping
    public List<ProductResponse> getAll() {
        return productService.getAll().stream().map(ProductResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ProductResponse getById(@PathVariable Long id) {
        return ProductResponse.from(productService.getById(id));
    }

    @PutMapping("/{id}")
    public ProductResponse update(
            @PathVariable Long id, @Valid @RequestBody UpdateProductRequest request) {
        return ProductResponse.from(productService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
