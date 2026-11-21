
package com.revo.backend.listing.controller;

import com.revo.backend.listing.dto.MyListingsResponse;
import com.revo.backend.listing.service.ListingService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me/listings")
public class MyListingsController {

    private final ListingService listingService;

    public MyListingsController(
            ListingService listingService
    ) {
        this.listingService = listingService;
    }

    @GetMapping
    public MyListingsResponse getMyListings(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String status
    ) {
        Long sellerId = Long.parseLong(jwt.getSubject());

        return listingService.getMyListings(
                sellerId,
                page,
                size,
                status
        );
    }
}
