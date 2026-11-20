package com.revo.backend.order.dto;

import com.revo.backend.order.entity.OrderStatus;
import com.revo.backend.order.entity.CodStatus;
import com.revo.backend.purchase.dto.PurchaseRequestResponse;

import java.time.OffsetDateTime;

public record OrderCardResponse(
        Long id,
        PurchaseRequestResponse.ListingRef listing,
        String roleInOrder,
        Long transactionPrice,
        OrderStatus status,
        CodStatus codStatus,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        boolean canCancel,
        boolean canConfirmCompletion
) {}