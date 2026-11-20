package com.revo.backend.listing.dto;

public record ListingSearchRequest(
        String page,
        String size,
        String q,
        String categoryId,
        String minPrice,
        String maxPrice,
        String condition,
        String province,
        String ward
) {}