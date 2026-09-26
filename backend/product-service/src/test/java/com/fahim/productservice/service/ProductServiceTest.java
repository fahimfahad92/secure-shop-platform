package com.fahim.productservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fahim.productservice.dto.CreateProductRequest;
import com.fahim.productservice.dto.UpdateProductRequest;
import com.fahim.productservice.exception.ProductNotFoundException;
import com.fahim.productservice.model.Product;
import com.fahim.productservice.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock private ProductRepository productRepository;

    @InjectMocks private ProductService productService;

    @Test
    void create_persistsAllFields() {
        CreateProductRequest request =
                new CreateProductRequest("Keyboard", "Mechanical", new BigDecimal("99.99"), 10);
        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Product result = productService.create(request);

        assertThat(result.getName()).isEqualTo("Keyboard");
        assertThat(result.getDescription()).isEqualTo("Mechanical");
        assertThat(result.getPrice()).isEqualByComparingTo("99.99");
        assertThat(result.getStock()).isEqualTo(10);
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void getById_found_returnsProduct() {
        Product product = new Product();
        product.setId(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        Product result = productService.getById(1L);

        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void getById_notFound_throwsProductNotFoundException() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getById(99L))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void update_overwritesEveryMutableField() {
        Product existing = new Product();
        existing.setId(1L);
        existing.setName("Old");
        existing.setPrice(new BigDecimal("10.00"));
        existing.setStock(1);
        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UpdateProductRequest request =
                new UpdateProductRequest("New", "Updated", new BigDecimal("25.50"), 7);
        Product result = productService.update(1L, request);

        assertThat(result.getName()).isEqualTo("New");
        assertThat(result.getDescription()).isEqualTo("Updated");
        assertThat(result.getPrice()).isEqualByComparingTo("25.50");
        assertThat(result.getStock()).isEqualTo(7);
    }

    @Test
    void update_notFound_throwsProductNotFoundException() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());
        UpdateProductRequest request =
                new UpdateProductRequest("New", null, new BigDecimal("1.00"), 1);

        assertThatThrownBy(() -> productService.update(99L, request))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void delete_removesExistingProduct() {
        Product existing = new Product();
        existing.setId(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));

        productService.delete(1L);

        verify(productRepository).delete(existing);
    }

    @Test
    void delete_notFound_throwsProductNotFoundException() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.delete(99L))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void getAll_returnsAllProducts() {
        when(productRepository.findAll()).thenReturn(List.of(new Product(), new Product()));

        List<Product> result = productService.getAll();

        assertThat(result).hasSize(2);
    }
}
