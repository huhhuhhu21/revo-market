package com.revo.backend.purchase.dto;

import java.util.List;

public record SentPurchaseRequestPage(
        List<SentPurchaseRequestItem> items,
        int page,
        int size,
        long totalElements,
        boolean hasNext
) {}