
package com.revo.backend.listing.dto;

import com.revo.backend.listing.entity.ItemCondition;
import com.revo.backend.listing.entity.ListingStatus;

import java.time.OffsetDateTime;
import java.util.List;

public record MyListingsResponse(
        List<OwnerListingCard> items,
        int page,
        int size,
        long totalElements,
        boolean hasNext
) {
    public record OwnerListingCard(
            Long id,
            String title,
            Long listedPrice,
            ItemCondition itemCondition,
            ListingSearchResponse.CategoryCard category,
            String province,
            String ward,
            String thumbnailUrl,
            ListingStatus status,
            OffsetDateTime createdAt,
            String hiddenReasonType,
            boolean isAdminHidden,
            boolean isDeleted,
            boolean canEdit,
            boolean canHide,
            boolean canRestore,
            boolean canDelete,
            boolean canMarkSold,
            String lockReason
    ) {}
}
