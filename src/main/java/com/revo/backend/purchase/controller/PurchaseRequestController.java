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
import com.revo.backend.purchase.dto.SentPurchaseRequestItem;
import com.revo.backend.purchase.dto.ReceivedPurchaseRequestItem;
import java.util.List;

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
    public List<SentPurchaseRequestItem> getSentRequests(
            @RequestParam(defaultValue = "ALL") String status,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long buyerId = Long.parseLong(jwt.getSubject());

        return service.getSentRequests(buyerId, status);
    }

    @GetMapping("/received")
    public List<ReceivedPurchaseRequestItem> getReceivedRequests(
            @RequestParam(defaultValue = "ALL") String status,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long sellerId = Long.parseLong(jwt.getSubject());

        return service.getReceivedRequests(sellerId, status);
    }
}
