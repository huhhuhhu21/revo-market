
package com.revo.backend.order.controller;

import com.revo.backend.order.dto.CreateReturnRequest;
import com.revo.backend.order.dto.ReturnRequestResponse;
import com.revo.backend.order.service.ReturnRequestService;
import com.revo.backend.order.service.ReturnRequestService.CreateResult;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
public class ReturnRequestController {

    private final ReturnRequestService returnRequestService;

    public ReturnRequestController(
            ReturnRequestService returnRequestService
    ) {
        this.returnRequestService = returnRequestService;
    }

    @PostMapping("/{orderId}/returns")
    public ResponseEntity<ReturnRequestResponse> create(
            @PathVariable String orderId,
            @Valid @RequestBody CreateReturnRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long currentUserId =
                Long.parseLong(jwt.getSubject());

        CreateResult result = returnRequestService.create(
                currentUserId,
                orderId,
                request
        );

        return ResponseEntity
                .status(
                        result.created()
                                ? HttpStatus.CREATED
                                : HttpStatus.OK
                )
                .body(result.response());
    }
}
