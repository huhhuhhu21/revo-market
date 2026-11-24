
package com.revo.backend.order.controller;

import com.revo.backend.common.api.ApiErrorResponse;
import com.revo.backend.common.exception.ErrorCode;
import com.revo.backend.order.dto.AdminReturnHandoverRequest;
import com.revo.backend.order.service.ReturnRequestService;
import com.revo.backend.order.service.ReturnRequestService.HandoverOutcome;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/returns")
public class AdminReturnHandoverController {

    private final ReturnRequestService returnRequestService;

    public AdminReturnHandoverController(
            ReturnRequestService returnRequestService
    ) {
        this.returnRequestService = returnRequestService;
    }

    @PostMapping("/{returnId}/handover")
    public ResponseEntity<?> handover(
            @PathVariable String returnId,
            @Valid @RequestBody
            AdminReturnHandoverRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest httpRequest
    ) {
        Long adminId = Long.parseLong(
                jwt.getSubject()
        );

        HandoverOutcome result =
                returnRequestService.recordReturnHandover(
                        adminId,
                        returnId,
                        request
                );

        if (result.exceptionId() != null) {
            // Transaction trong service đã kết thúc
            // trước khi controller trả HTTP 409.
            ErrorCode code =
                    ErrorCode.RETURN_HANDOVER_REQUIRES_EXCEPTION;

            Object value =
                    httpRequest.getAttribute("requestId");

            String requestId =
                    value instanceof String id && !id.isBlank()
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
