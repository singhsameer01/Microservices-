package com.learn.order.config;

import com.learn.order.exception.ProductNotFoundException;
import feign.RequestInterceptor;
import feign.Response;
import feign.codec.ErrorDecoder;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign client configuration.
 *
 * Registers:
 * 1. A custom ErrorDecoder that maps HTTP 404 from product-service to ProductNotFoundException.
 * 2. A RequestInterceptor that relays the incoming Bearer token to downstream Feign calls
 *    so that product-service and payment-service can validate it via the JWKS endpoint
 *    (token relay — the core of SSO between services).
 */
@Configuration
public class FeignConfig {

    @Bean
    public ErrorDecoder errorDecoder() {
        return new ErrorDecoder() {
            private final ErrorDecoder defaultDecoder = new Default();

            @Override
            public Exception decode(String methodKey, Response response) {
                if (response.status() == 404 && methodKey.contains("ProductClient")) {
                    return new ProductNotFoundException("Product not found (404 from product-service)");
                }
                return defaultDecoder.decode(methodKey, response);
            }
        };
    }

    @Bean
    public RequestInterceptor tokenRelayInterceptor() {
        return requestTemplate -> {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String authHeader = request.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    requestTemplate.header("Authorization", authHeader);
                }
            }
        };
    }
}
