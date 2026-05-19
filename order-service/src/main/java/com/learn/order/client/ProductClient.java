package com.learn.order.client;

import com.learn.order.dto.ProductResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client for product-service.
 *
 * <p>The {@code name} attribute must exactly match {@code spring.application.name}
 * in product-service's application.yml ("product-service"). Eureka resolves
 * the logical name to a real host:port via load-balancing ({@code lb://}).</p>
 *
 * <p>If product-service returns 404, the {@code ProductClientErrorDecoder} maps it
 * to a {@code ProductNotFoundException} so callers get a meaningful exception.</p>
 */
@FeignClient(name = "product-service")
public interface ProductClient {

    /**
     * Fetches a product by ID from product-service.
     *
     * @param id the product's database ID
     * @return the product details including name and price
     */
    @GetMapping("/api/v1/products/{id}")
    ProductResponse getProductById(@PathVariable("id") Long id);
}
