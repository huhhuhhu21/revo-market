package com.revo.backend.order.controller;

import com.revo.backend.order.dto.AdminReturnHandoverRequest;
import com.revo.backend.order.dto.ReturnRequestResponse;
import com.revo.backend.order.service.ReturnRequestService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/returns")
public class AdminReturnHandoverController {

    private final ReturnRequestService returnRequestService;

    public AdminReturnHandoverController(ReturnRequestService returnRequestService) {
        this.returnRequestService = returnRequestService;
    }

    @PostMapping("/{returnId}/handover")
    public ResponseEntity<ReturnRequestResponse> handover(
            @PathVariable String returnId,
            @Valid @RequestBody AdminReturnHandoverRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long adminId = Long.parseLong(jwt.getSubject());
        return ResponseEntity.ok(returnRequestService.recordReturnHandover(
                adminId, returnId, request
        ));
    }
}
