package com.revo.backend.listing.controller;

import com.revo.backend.listing.dto.*;
import com.revo.backend.listing.service.ListingService;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/listings")
public class ListingController {

    private final ListingService listingService;

    public ListingController(ListingService listingService) {
        this.listingService = listingService;
    }

    @GetMapping
    public ListingSearchResponse search(
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String minPrice,
            @RequestParam(required = false) String maxPrice,
            @RequestParam(required = false) String condition,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String ward
    ) {
        ListingSearchRequest request = new ListingSearchRequest(
                page,
                size,
                q,
                categoryId,
                minPrice,
                maxPrice,
                condition,
                province,
                ward
        );

        return listingService.search(request);
    }
}