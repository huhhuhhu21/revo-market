package com.revo.backend.order.controller;

import com.revo.backend.order.dto.AdminReturnReceiptRequest;
import com.revo.backend.order.dto.AdminReturnReceiptResponse;
import com.revo.backend.order.service.ReturnRequestService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/returns")
public class AdminReturnReceiptController {

    private final ReturnRequestService returnRequestService;

    public AdminReturnReceiptController(ReturnRequestService returnRequestService) {
        this.returnRequestService = returnRequestService;
    }

    @PostMapping("/{returnId}/receipt")
    public ResponseEntity<AdminReturnReceiptResponse> receipt(
            @PathVariable String returnId,
            @Valid @RequestBody AdminReturnReceiptRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long adminId = Long.parseLong(jwt.getSubject());
        return ResponseEntity.ok(returnRequestService.recordReturnReceipt(
                adminId, returnId, request
        ));
    }
}
