
package com.revo.backend.order.dto;

import com.revo.backend.order.entity.OrderStatus;
import com.revo.backend.order.entity.CodStatus;

import java.time.OffsetDateTime;
import java.util.List;

public record AdminOrderPageResponse(
        List<AdminOrderItem> items,
        int page,
        int size,
        long totalElements,
        boolean hasNext
) {

    public record AdminOrderItem(
            Long id,
            Long buyerId,
            Long sellerId,
            OrderStatus status,
            CodStatus codStatus,
            Long transactionPrice,
            OffsetDateTime pickedUpAt,
            OffsetDateTime deliveredAt,
            boolean hasCancellationPending,
            boolean hasPendingException,
            OffsetDateTime createdAt
    ) {}
}
