package com.revo.backend.order.controller;

import com.revo.backend.order.dto.AdminReturnShippingRequest;
import com.revo.backend.order.dto.AdminReturnShippingResponse;
import com.revo.backend.order.service.AdminReturnShippingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminReturnShippingController {

    private final AdminReturnShippingService service;

    public AdminReturnShippingController(AdminReturnShippingService service) {
        this.service = service;
    }

    @PostMapping("/{orderId}/return-shipping/complete")
    public ResponseEntity<AdminReturnShippingResponse> complete(
            @PathVariable String orderId,
            @Valid @RequestBody AdminReturnShippingRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long adminId = Long.parseLong(jwt.getSubject());
        return ResponseEntity.ok(service.complete(adminId, orderId, request));
    }
}
