package com.revo.backend.purchase.dto;

import jakarta.validation.constraints.*;

public record CreatePurchaseRequestRequest(
        @NotNull @Positive Long listingId,
        @NotNull @Positive Long offeredPrice,
        @Size(max = 2000) String note,
        @NotBlank @Size(max = 120) String recipientName,
        @NotBlank @Size(max = 20) String recipientPhone,
        @NotBlank @Size(max = 255) String deliveryAddressLine,
        @NotBlank @Size(max = 100) String deliveryProvince,
        @NotBlank @Size(max = 100) String deliveryWard
) {}
