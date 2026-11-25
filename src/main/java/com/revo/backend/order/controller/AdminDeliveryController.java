package com.revo.backend.order.controller;

import com.revo.backend.order.dto.AdminDeliveryRequest;
import com.revo.backend.order.dto.AdminDeliveryResponse;
import com.revo.backend.order.service.AdminDeliveryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminDeliveryController {

    private final AdminDeliveryService deliveryService;

    public AdminDeliveryController(AdminDeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @PostMapping("/{orderId}/deliver")
    public ResponseEntity<AdminDeliveryResponse> deliver(
            @PathVariable String orderId,
            @Valid @RequestBody AdminDeliveryRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long adminId = Long.parseLong(jwt.getSubject());
        return ResponseEntity.ok(
                deliveryService.deliver(adminId, orderId, request)
        );
    }
}
