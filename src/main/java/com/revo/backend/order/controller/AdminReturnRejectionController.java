
package com.revo.backend.order.controller;

import com.revo.backend.order.dto.RejectReturnRequest;
import com.revo.backend.order.dto.ReturnRequestResponse;
import com.revo.backend.order.service.ReturnRequestService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/returns")
public class AdminReturnRejectionController {

    private final ReturnRequestService returnRequestService;

    public AdminReturnRejectionController(
            ReturnRequestService returnRequestService
    ) {
        this.returnRequestService = returnRequestService;
    }

    @PostMapping("/{returnId}/reject")
    public ResponseEntity<ReturnRequestResponse> reject(
            @PathVariable String returnId,
            @Valid @RequestBody RejectReturnRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long adminId = Long.parseLong(
                jwt.getSubject()
        );

        ReturnRequestResponse response =
                returnRequestService.rejectReturn(
                        adminId,
                        returnId,
                        request
                );

        return ResponseEntity.ok(response);
    }
}
