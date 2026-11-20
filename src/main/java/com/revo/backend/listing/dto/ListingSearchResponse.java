package com.revo.backend.listing.dto;

import com.revo.backend.listing.entity.ItemCondition;
import com.revo.backend.listing.entity.ListingStatus;

import java.time.OffsetDateTime;
import java.util.List;

public record ListingSearchResponse(
        List<ListingCard> items,
        int page,
        int size,
        long totalElements,
        boolean hasNext
) {
    public record ListingCard(
            Long id,
            String title,
            Long listedPrice,
            ItemCondition itemCondition,
            CategoryCard category,
            String province,
            String ward,
            String thumbnailUrl,
            ListingStatus status,
            OffsetDateTime createdAt
    ) {}

    public record CategoryCard(
            Long id,
            String name
    ) {}
}