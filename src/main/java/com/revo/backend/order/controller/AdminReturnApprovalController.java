
package com.revo.backend.order.controller;

import com.revo.backend.order.dto.ApproveReturnRequest;
import com.revo.backend.order.dto.ReturnRequestResponse;
import com.revo.backend.order.service.ReturnRequestService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/returns")
public class AdminReturnApprovalController {

    private final ReturnRequestService returnRequestService;

    public AdminReturnApprovalController(
            ReturnRequestService returnRequestService
    ) {
        this.returnRequestService = returnRequestService;
    }

    @PostMapping("/{returnId}/approve")
    public ResponseEntity<ReturnRequestResponse> approve(
            @PathVariable String returnId,
            @Valid @RequestBody(required = false)
            ApproveReturnRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long adminId = Long.parseLong(
                jwt.getSubject()
        );

        ReturnRequestResponse response =
                returnRequestService.approveReturn(
                        adminId,
                        returnId,
                        request
                );

        return ResponseEntity.ok(response);
    }
}
