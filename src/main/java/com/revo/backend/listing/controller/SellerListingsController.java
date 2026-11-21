
package com.revo.backend.listing.controller;

import com.revo.backend.listing.dto.ListingSearchResponse;
import com.revo.backend.listing.service.ListingService;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class SellerListingsController {

    private final ListingService listingService;

    public SellerListingsController(
            ListingService listingService
    ) {
        this.listingService = listingService;
    }

    @GetMapping("/{userId}/listings")
    public ListingSearchResponse getSellerListings(
            @PathVariable Long userId,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String status
    ) {
        return listingService.getSellerPublicListings(
                userId,
                page,
                size,
                status
        );
    }
}
