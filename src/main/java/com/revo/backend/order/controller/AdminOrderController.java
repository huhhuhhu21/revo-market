
package com.revo.backend.order.controller;

import com.revo.backend.order.dto.AdminOrderPageResponse;
import com.revo.backend.order.service.AdminOrderService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    public AdminOrderController(
            AdminOrderService adminOrderService
    ) {
        this.adminOrderService = adminOrderService;
    }

    @GetMapping
    public AdminOrderPageResponse getOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String codStatus,
            @RequestParam(required = false) String orderId,
            @AuthenticationPrincipal Jwt jwt
    ) {

        Long adminId = Long.parseLong(jwt.getSubject());

        return adminOrderService.getAdminOrders(
                adminId,
                page,
                size,
                status,
                codStatus,
                orderId
        );
    }
}
