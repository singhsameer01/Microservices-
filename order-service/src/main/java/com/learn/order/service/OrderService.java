package com.learn.order.service;

import com.learn.order.dto.*;
import com.learn.order.exception.OrderNotFoundException;
import com.learn.order.model.Order;
import com.learn.order.model.OrderItem;
import com.learn.order.repository.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public OrderResponse placeOrder(OrderRequest request) {
        Order order = new Order(request.customerId());

        for (OrderItemDto itemDto : request.items()) {
            // Once Feign is implemented: call ProductClient to validate product exists
            // and get the real price — replace itemDto.price() with the fetched price
            OrderItem item = new OrderItem(
                    itemDto.productId(),
                    "Product-" + itemDto.productId(),  // replace with product name from ProductClient
                    itemDto.quantity(),
                    itemDto.price()
            );
            order.addItem(item);
        }

        // Once Feign + Circuit Breaker are implemented:
        // call PaymentClient.processPayment() and update order status accordingly

        Order saved = orderRepository.save(order);

        // Once Kafka is implemented: publish OrderPlacedEvent here

        return toResponse(saved);
    }

    public OrderResponse findById(Long id) {
        return orderRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

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
