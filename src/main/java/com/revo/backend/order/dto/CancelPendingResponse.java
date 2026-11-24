
package com.revo.backend.order.dto;

import com.revo.backend.order.entity.OrderStatus;
import java.time.OffsetDateTime;

public record CancelPendingResponse(
        Long orderId,
        OrderStatus orderStatus,
        String cancellationStatus,
        OffsetDateTime requestedAt,
        String message
) {}
