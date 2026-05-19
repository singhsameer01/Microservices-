package com.learn.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn.order.client.ProductClient;
import com.learn.order.dto.*;
import com.learn.order.event.OrderPlacedEvent;
import com.learn.order.exception.OrderNotFoundException;
import com.learn.order.model.Order;
import com.learn.order.model.OrderItem;
import com.learn.order.outbox.OutboxEvent;
import com.learn.order.outbox.OutboxEventRepository;
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
 * Order placement flow:
 * 1. Validate products via Feign → product-service
 * 2. Save order as PENDING
 * 3. Initiate payment via Feign → payment-service (circuit-breaker + bulkhead + retry wrapped)
 * 4. Save OrderPlacedEvent to outbox_events table in the same DB transaction.
 *    OutboxEventPublisher polls unpublished events and sends them to Kafka every 5 seconds,
 *    guaranteeing delivery even if the service crashes between steps 3 and 4.
 */
@Service
@Transactional(readOnly = true)
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final PaymentProcessor paymentProcessor;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OrderService(
            OrderRepository orderRepository,
            ProductClient productClient,
            PaymentProcessor paymentProcessor,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.productClient = productClient;
        this.paymentProcessor = paymentProcessor;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OrderResponse placeOrder(OrderRequest request) {
        Order order = new Order(request.customerId());

        for (OrderItemDto itemDto : request.items()) {
            ProductResponse product = productClient.getProductById(itemDto.productId());
            OrderItem item = new OrderItem(
                    product.id(),
                    product.name(),
                    itemDto.quantity(),
                    product.price()
            );
            order.addItem(item);
        }

        Order saved = orderRepository.save(order);
        log.info("Order saved with id={} status=PENDING for customerId={}", saved.getId(), saved.getCustomerId());

        paymentProcessor.processPayment(saved);

        saveToOutbox(saved);

        return toResponse(saved);
    }

    private void saveToOutbox(Order order) {
        try {
            OrderPlacedEvent event = new OrderPlacedEvent(
                    order.getId(), order.getCustomerId(), order.getTotalAmount(), Instant.now());
            String payload = objectMapper.writeValueAsString(event);
            outboxEventRepository.save(new OutboxEvent("ORDER", order.getId(), "ORDER_PLACED", payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize OrderPlacedEvent for order " + order.getId(), e);
        }
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
