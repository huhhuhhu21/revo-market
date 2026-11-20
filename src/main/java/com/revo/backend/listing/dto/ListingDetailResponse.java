package com.revo.backend.listing.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.revo.backend.category.CategoryStatus;
import com.revo.backend.listing.entity.ItemCondition;
import com.revo.backend.listing.entity.ListingStatus;

import java.time.OffsetDateTime;
import java.util.List;

public record ListingDetailResponse(
        Long id,
        SellerInfo seller,
        CategoryInfo category,
        String title,
        String description,
        Long listedPrice,
        ItemCondition itemCondition,
        String province,
        String ward,
        ListingStatus status,
        List<ImageInfo> images,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        String hiddenReasonType,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        Boolean isAdminHidden,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        Boolean isDeleted,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        Boolean canEdit,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        Boolean canHide,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        Boolean canRestore,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        Boolean canDelete,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        Boolean canMarkSold,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        String lockReason
) {

    public record SellerInfo(
            Long id,
            String fullName,
            String avatarUrl
    ) {}

    public record CategoryInfo(
            Long id,
            String name,
            CategoryStatus status
    ) {}

    public record ImageInfo(
            Long id,
            String url,
            Integer sortOrder
    ) {}
}