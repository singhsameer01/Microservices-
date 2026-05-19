package com.learn.order.service;

import com.learn.order.client.ProductClient;
import com.learn.order.dto.*;
import com.learn.order.event.OrderPlacedEvent;
import com.learn.order.exception.OrderNotFoundException;
import com.learn.order.messaging.OrderEventPublisher;
import com.learn.order.model.Order;
import com.learn.order.model.OrderItem;
import com.learn.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Core business logic for order management.
 *
 * <p>The order placement flow:
 * <ol>
 *   <li>Validate each requested product via Feign → product-service (ProductClient)</li>
 *   <li>Save the order as PENDING in the local H2 database</li>
 *   <li>Initiate payment via Feign → payment-service (PaymentClient), wrapped in a
 *       Resilience4j circuit breaker; if the circuit is OPEN or payment fails the order
 *       is marked CANCELLED via the fallback</li>
 *   <li>Publish an {@link OrderPlacedEvent} to the "order-placed" Kafka topic so
 *       notification-service can send a confirmation</li>
 * </ol>
 * </p>
 */
@Service
@Transactional(readOnly = true)
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final PaymentProcessor paymentProcessor;
    private final OrderEventPublisher eventPublisher;

    public OrderService(
            OrderRepository orderRepository,
            ProductClient productClient,
            PaymentProcessor paymentProcessor,
            OrderEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.productClient = productClient;
        this.paymentProcessor = paymentProcessor;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Places a new order for a customer.
     *
     * <p>Validates each product against product-service (real name and price are fetched),
     * saves the order, calls payment-service through a circuit breaker, and publishes
     * an OrderPlacedEvent to Kafka on success.</p>
     *
     * @param request validated order payload (customerId + list of items)
     * @return the saved order with confirmed total amount and status
     */
    @Transactional
    public OrderResponse placeOrder(OrderRequest request) {
        Order order = new Order(request.customerId());

        for (OrderItemDto itemDto : request.items()) {
            // Validate product exists and get real name/price from product-service
            ProductResponse product = productClient.getProductById(itemDto.productId());
            OrderItem item = new OrderItem(
                    product.id(),
                    product.name(),
                    itemDto.quantity(),
                    product.price()          // use authoritative price from product-service
            );
            order.addItem(item);
        }

        Order saved = orderRepository.save(order);
        log.info("Order saved with id={} status=PENDING for customerId={}", saved.getId(), saved.getCustomerId());

        // Delegate to PaymentProcessor — a separate bean so Spring AOP proxy intercepts @CircuitBreaker
        paymentProcessor.processPayment(saved);

        // Publish event regardless of payment status — notification-service reacts to it
        eventPublisher.publishOrderPlaced(new OrderPlacedEvent(
                saved.getId(),
                saved.getCustomerId(),
                saved.getTotalAmount(),
                Instant.now()
        ));

        return toResponse(saved);
    }

    /**
     * Returns the full order details by ID.
     *
     * @param id the order's database ID
     * @return the order response DTO
     * @throws com.learn.order.exception.OrderNotFoundException if no order with this ID exists
     */
    public OrderResponse findById(Long id) {
        return orderRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    /**
     * Returns a paginated list of orders for a specific customer.
     *
     * @param customerId the customer's identifier
     * @param pageable   pagination and sorting parameters
     * @return a page of order response DTOs
     */
    public Page<OrderResponse> findByCustomer(String customerId, Pageable pageable) {
        return orderRepository.findByCustomerId(customerId, pageable).map(this::toResponse);
    }

    private OrderResponse toResponse(Order o) {
        List<OrderItemResponse> items = o.getItems().stream()
                .map(i -> new OrderItemResponse(i.getId(), i.getProductId(),
                        i.getProductName(), i.getQuantity(), i.getPrice()))
                .toList();
        return new OrderResponse(o.getId(), o.getCustomerId(), o.getStatus(),
                o.getTotalAmount(), o.getCreatedAt(), items);
    }
}
