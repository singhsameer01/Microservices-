package com.learn.product.service;

import com.learn.product.dto.ProductRequest;
import com.learn.product.dto.ProductResponse;
import com.learn.product.exception.ProductNotFoundException;
import com.learn.product.model.Product;
import com.learn.product.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Product product = new Product(
                request.name(), request.description(),
                request.price(), request.stock(), request.category()
        );
        return toResponse(productRepository.save(product));
    }

    public ProductResponse findById(Long id) {
        return productRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    public Page<ProductResponse> findAll(String category, Pageable pageable) {
        if (category != null && !category.isBlank()) {
            return productRepository.findByCategory(category, pageable).map(this::toResponse);
        }
        return productRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStock(request.stock());
        product.setCategory(request.category());
        return toResponse(productRepository.save(product));
    }

    @Transactional
    public void delete(Long id) {
        if (!productRepository.existsById(id)) {
            throw new ProductNotFoundException(id);
        }
        productRepository.deleteById(id);
    }

    private ProductResponse toResponse(Product p) {
        return new ProductResponse(p.getId(), p.getName(), p.getDescription(),
                p.getPrice(), p.getStock(), p.getCategory());
    }
}
