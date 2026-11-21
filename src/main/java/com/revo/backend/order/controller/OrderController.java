package com.revo.backend.order.controller;

import com.revo.backend.order.dto.OrderCardResponse;
import com.revo.backend.order.service.OrderService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import com.revo.backend.order.dto.OrderDetailResponse;
import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public ResponseEntity<List<OrderCardResponse>> getOrders(
            @RequestParam(defaultValue = "ALL") String role,
            @RequestParam(defaultValue = "ALL") String status,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long currentUserId = Long.parseLong(
                jwt.getSubject()
        );

        List<OrderCardResponse> result =
                orderService.getOrders(
                        currentUserId,
                        role,
                        status
                );

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDetailResponse> getOrderDetail(
            @PathVariable String orderId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long currentUserId = Long.parseLong(
                jwt.getSubject()
        );

        OrderDetailResponse result =
                orderService.getOrderDetail(
                        currentUserId,
                        orderId
                );

        return ResponseEntity.ok(result);
    }
}