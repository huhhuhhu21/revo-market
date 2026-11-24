
package com.revo.backend.order.controller;

import com.revo.backend.common.api.ApiErrorResponse;
import com.revo.backend.common.exception.ErrorCode;
import com.revo.backend.order.dto.AdminDeliveryRequest;
import com.revo.backend.order.service.AdminDeliveryService;
import com.revo.backend.order.service.AdminDeliveryService.DeliveryOutcome;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminDeliveryController {

    private final AdminDeliveryService deliveryService;

    public AdminDeliveryController(
            AdminDeliveryService deliveryService
    ) {
        this.deliveryService = deliveryService;
    }

    @PostMapping("/{orderId}/deliver")
    public ResponseEntity<?> deliver(
            @PathVariable String orderId,
            @Valid @RequestBody AdminDeliveryRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest httpRequest
    ) {
        Long adminId = Long.parseLong(jwt.getSubject());

        DeliveryOutcome result = deliveryService.deliver(
                adminId,
                orderId,
                request
        );

        // Service đã commit ngoại lệ trước khi đến đây.
        if (result.exceptionId() != null) {

            ErrorCode code =
                    ErrorCode.DELIVERY_REQUIRES_EXCEPTION;

            String requestId = getRequestId(httpRequest);

            ApiErrorResponse body = ApiErrorResponse.of(
                    code.name(),
                    code.getDefaultMessage(),
                    List.of(
                            new ApiErrorResponse.FieldErrorDetail(
                                    "exceptionId",
                                    result.exceptionId().toString()
                            )
                    ),
                    requestId
            );

            return ResponseEntity
                    .status(code.getHttpStatus())
                    .body(body);
        }

        return ResponseEntity.ok(result.delivered());
    }

    private static String getRequestId(
            HttpServletRequest request
    ) {
        Object current = request.getAttribute("requestId");

        if (current instanceof String id && !id.isBlank()) {
            return id;
        }

        String id = "req-" + UUID.randomUUID();
        request.setAttribute("requestId", id);
        return id;
    }
}
