package com.revo.backend.purchase.dto;

import com.revo.backend.purchase.entity.PurchaseRequestStatus;
import java.time.OffsetDateTime;

public record ReceivedPurchaseRequestItem(
        Long id,
        PurchaseRequestResponse.ListingRef listing,
        PurchaseRequestResponse.Party buyer,
        Long offeredPrice,
        PurchaseRequestStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt,
        Long orderId,
        boolean canAccept,
        boolean canReject
) {}