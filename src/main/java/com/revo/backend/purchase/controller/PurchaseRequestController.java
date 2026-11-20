package com.revo.backend.purchase.controller;

import com.revo.backend.purchase.dto.CreatePurchaseRequestRequest;
import com.revo.backend.purchase.dto.PurchaseRequestResponse;
import com.revo.backend.purchase.service.PurchaseRequestService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import com.revo.backend.purchase.dto.SentPurchaseRequestPage;
@RestController
@RequestMapping("/api/v1/purchase-requests")
public class PurchaseRequestController {

    private final PurchaseRequestService service;

    public PurchaseRequestController(PurchaseRequestService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PurchaseRequestResponse> create(
            @Valid @RequestBody CreatePurchaseRequestRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long buyerId = Long.parseLong(jwt.getSubject());
        PurchaseRequestResponse result = service.create(buyerId, request);
        return ResponseEntity
                .created(URI.create("/api/v1/purchase-requests/" + result.id()))
                .body(result);
    }

    @GetMapping("/sent")
    public SentPurchaseRequestPage getSentRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "ALL") String status,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long buyerId = Long.parseLong(jwt.getSubject());

        return service.getSentRequests(
                buyerId,
                page,
                size,
                status
        );
    }
}
