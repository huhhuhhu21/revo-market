package com.revo.backend.purchase.dto;

import com.revo.backend.purchase.entity.PurchaseRequestStatus;
import java.time.OffsetDateTime;

public record SentPurchaseRequestItem(
        Long id,
        PurchaseRequestResponse.ListingRef listing,
        Long offeredPrice,
        PurchaseRequestStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt,
        Long orderId,
        boolean canWithdraw
) {}