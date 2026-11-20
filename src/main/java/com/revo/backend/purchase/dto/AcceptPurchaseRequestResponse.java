package com.revo.backend.purchase.dto;

import com.revo.backend.order.entity.CodStatus;
import com.revo.backend.order.entity.OrderStatus;
import com.revo.backend.purchase.entity.PurchaseRequestStatus;

import java.time.OffsetDateTime;

public record AcceptPurchaseRequestResponse(
        Long purchaseRequestId,
        PurchaseRequestStatus purchaseRequestStatus,
        OrderSummary order
) {
    public record OrderSummary(
            Long id,
            OrderStatus status,
            CodStatus codStatus,
            Long transactionPrice,
            Long shippingFee,
            Long codAmount,
            OffsetDateTime createdAt,
            OffsetDateTime pickupDeadline
    ) {}
}