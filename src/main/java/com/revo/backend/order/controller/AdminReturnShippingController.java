
package com.revo.backend.order.controller;

import com.revo.backend.common.api.ApiErrorResponse;
import com.revo.backend.common.exception.ErrorCode;
import com.revo.backend.order.dto.AdminReturnShippingRequest;
import com.revo.backend.order.service.AdminReturnShippingService;
import com.revo.backend.order.service.AdminReturnShippingService.CompletionOutcome;

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
public class AdminReturnShippingController {

    private final AdminReturnShippingService service;

    public AdminReturnShippingController(
            AdminReturnShippingService service
    ) {
        this.service = service;
    }

    @PostMapping("/{orderId}/return-shipping/complete")
    public ResponseEntity<?> complete(
            @PathVariable String orderId,
            @Valid @RequestBody
            AdminReturnShippingRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest httpRequest
    ) {
        Long adminId = Long.parseLong(jwt.getSubject());

        CompletionOutcome result = service.complete(
                adminId,
                orderId,
                request
        );

        // Ngoại lệ đã được commit trong service.
        if (result.exceptionId() != null) {
            ErrorCode code =
                    ErrorCode.RETURN_SHIPPING_REQUIRES_EXCEPTION;

            Object existing =
                    httpRequest.getAttribute("requestId");

            String requestId =
                    existing instanceof String id && !id.isBlank()
                            ? id
                            : "req-" + UUID.randomUUID();

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

        return ResponseEntity.ok(result.response());
    }
}
