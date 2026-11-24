
package com.revo.backend.order.dto;

import com.revo.backend.order.entity.OrderStatus;
import com.revo.backend.order.entity.CodStatus;

import java.time.OffsetDateTime;
import java.util.List;

public record AdminDeliveryResponse(
        Long id,
        Long buyerId,
        Long sellerId,
        OrderStatus status,
        CodStatus codStatus,
        Long transactionPrice,
        Long codAmount,
        OffsetDateTime pickedUpAt,
        OffsetDateTime deliveredAt,
        OffsetDateTime codCollectedAt,
        boolean hasCancellationPending,
        boolean hasPendingException,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<OrderDetailResponse.ShippingEvent> shippingTimeline
) {}
