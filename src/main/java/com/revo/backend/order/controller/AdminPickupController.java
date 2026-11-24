
package com.revo.backend.order.controller;

import com.revo.backend.order.dto.AdminPickupRequest;
import com.revo.backend.order.dto.AdminPickupResponse;
import com.revo.backend.order.service.AdminPickupService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminPickupController {

    private final AdminPickupService adminPickupService;

    public AdminPickupController(
            AdminPickupService adminPickupService
    ) {
        this.adminPickupService = adminPickupService;
    }

    @PostMapping("/{orderId}/pickup")
    public ResponseEntity<AdminPickupResponse> pickup(
            @PathVariable String orderId,
            @Valid @RequestBody AdminPickupRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long adminId = Long.parseLong(
                jwt.getSubject()
        );

        AdminPickupResponse result =
                adminPickupService.pickupOrder(
                        adminId,
                        orderId,
                        request
                );

        return ResponseEntity.ok(result);
    }
}
