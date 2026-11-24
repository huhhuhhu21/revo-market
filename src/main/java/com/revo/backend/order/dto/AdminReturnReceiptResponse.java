
package com.revo.backend.order.dto;

import com.revo.backend.order.entity.ReturnStatus;
import com.revo.backend.order.entity.OrderStatus;
import com.revo.backend.order.entity.CodStatus;
import com.revo.backend.listing.entity.ListingStatus;

import java.time.OffsetDateTime;

public record AdminReturnReceiptResponse(
        ReturnInfo returnRequest,
        OrderInfo order,
        ListingInfo listing
) {
    public record ReturnInfo(
            Long id,
            Long orderId,
            ReturnStatus status,
            OffsetDateTime returnedAt
    ) {}

    public record OrderInfo(
            Long id,
            OrderStatus status,
            CodStatus codStatus,
            OffsetDateTime codRefundedAt,
            Long codAmount
    ) {}

    public record ListingInfo(
            Long id,
            ListingStatus status,
            String hiddenReasonType
    ) {}
}
