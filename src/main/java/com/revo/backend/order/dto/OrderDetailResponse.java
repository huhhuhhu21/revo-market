package com.revo.backend.order.dto;

import com.revo.backend.order.entity.CodStatus;
import com.revo.backend.order.entity.OrderStatus;
import com.revo.backend.purchase.dto.PurchaseRequestResponse;

import java.time.OffsetDateTime;
import java.util.List;

public record OrderDetailResponse(
        Long id,
        Long purchaseRequestId,

        PurchaseRequestResponse.ListingRef listing,
        PurchaseRequestResponse.Party buyer,
        PurchaseRequestResponse.Party seller,

        Long transactionPrice,
        Long shippingFee,
        Long codAmount,

        OrderStatus status,
        CodStatus codStatus,

        PurchaseRequestResponse.DeliveryContact deliveryContact,

        OffsetDateTime pickupDeadline,
        OffsetDateTime pickedUpAt,
        OffsetDateTime deliveredAt,
        OffsetDateTime completedAt,

        OffsetDateTime canceledAt,
        String cancelReason,
        CancelActor canceledBy,

        OffsetDateTime codCollectedAt,
        OffsetDateTime codSettledAt,
        OffsetDateTime codRefundedAt,

        Cancellation cancellation,
        List<ShippingEvent> shippingTimeline,

        Long returnRequestId,

        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,

        boolean canCancel,
        boolean canConfirmCompletion,
        boolean canRequestReturn
) {

    public record CancelActor(
            Long id,
            String actorType
    ) {}

    public record Cancellation(
            boolean returnShippingPending,
            OffsetDateTime requestedAt
    ) {}

    public record ShippingEvent(
            String eventType,
            OffsetDateTime actualAt,
            OffsetDateTime recordedAt,
            String actorType
    ) {}
}