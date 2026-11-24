
package com.revo.backend.order.controller;

import com.revo.backend.common.api.ApiErrorResponse;
import com.revo.backend.common.exception.ErrorCode;
import com.revo.backend.order.dto.AdminReturnReceiptRequest;
import com.revo.backend.order.service.ReturnRequestService;
import com.revo.backend.order.service.ReturnRequestService.ReceiptOutcome;

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
public class AdminReturnReceiptController {

    private final ReturnRequestService returnRequestService;

    public AdminReturnReceiptController(
            ReturnRequestService returnRequestService
    ) {
        this.returnRequestService = returnRequestService;
    }

    @PostMapping("/{returnId}/receipt")
    public ResponseEntity<?> receipt(
            @PathVariable String returnId,
            @Valid @RequestBody
            AdminReturnReceiptRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest httpRequest
    ) {
        Long adminId = Long.parseLong(
                jwt.getSubject()
        );

        ReceiptOutcome result =
                returnRequestService.recordReturnReceipt(
                        adminId,
                        returnId,
                        request
                );

        // Ngoại lệ COD đã được commit trong service.
        if (result.exceptionId() != null) {
            ErrorCode code = ErrorCode.COD_ALREADY_SETTLED;

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
