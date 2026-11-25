
package com.revo.backend.order.dto;

import com.revo.backend.listing.entity.ListingStatus;
import com.revo.backend.order.entity.CodStatus;
import com.revo.backend.order.entity.OrderStatus;

import java.time.OffsetDateTime;
import java.util.List;

public record AdminReturnShippingResponse(
        Long id,
        Long buyerId,
        Long sellerId,
        Long listingId,

        OrderStatus status,
        CodStatus codStatus,
        Long transactionPrice,

        OffsetDateTime pickedUpAt,
        OffsetDateTime deliveredAt,
        OffsetDateTime canceledAt,
        Long canceledBy,
        String cancelReason,

        ListingStatus listingStatus,
        boolean listingPubliclyVisible,

        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,

        List<OrderDetailResponse.ShippingEvent> shippingTimeline
) {}
